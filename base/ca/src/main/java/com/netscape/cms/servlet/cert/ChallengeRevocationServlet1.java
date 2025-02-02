// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cms.servlet.cert;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authorization.AuthzToken;
import org.dogtagpki.server.ca.CAEngine;
import org.mozilla.jss.netscape.security.x509.CRLExtensions;
import org.mozilla.jss.netscape.security.x509.CRLReasonExtension;
import org.mozilla.jss.netscape.security.x509.InvalidityDateExtension;
import org.mozilla.jss.netscape.security.x509.RevocationReason;
import org.mozilla.jss.netscape.security.x509.RevokedCertImpl;
import org.mozilla.jss.netscape.security.x509.X509CertImpl;

import com.netscape.ca.CRLIssuingPoint;
import com.netscape.ca.CertificateAuthority;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IArgBlock;
import com.netscape.certsrv.logging.AuditFormat;
import com.netscape.certsrv.request.RequestStatus;
import com.netscape.cms.servlet.base.CMSServlet;
import com.netscape.cms.servlet.common.CMSRequest;
import com.netscape.cms.servlet.common.CMSTemplate;
import com.netscape.cms.servlet.common.CMSTemplateParams;
import com.netscape.cms.servlet.common.ECMSGWException;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.authentication.AuthSubsystem;
import com.netscape.cmscore.base.ArgBlock;
import com.netscape.cmscore.dbs.CertRecord;
import com.netscape.cmscore.dbs.CertRecordList;
import com.netscape.cmscore.dbs.CertificateRepository;
import com.netscape.cmscore.ldap.CAPublisherProcessor;
import com.netscape.cmscore.request.CertRequestRepository;
import com.netscape.cmscore.request.Request;
import com.netscape.cmscore.request.RequestQueue;

/**
 * Takes the certificate info (serial number) and optional challenge phrase, creates a
 * revocation request and submits it to the authority subsystem for processing
 *
 * @version $Revision$, $Date$
 */
public class ChallengeRevocationServlet1 extends CMSServlet {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChallengeRevocationServlet1.class);

    private static final long serialVersionUID = 1253319999546210407L;
    public final static String GETCERTS_FOR_CHALLENGE_REQUEST = "getCertsForChallenge";
    public static final String TOKEN_CERT_SERIAL = "certSerialToRevoke";
    // revocation templates.
    private final static String TPL_FILE = "revocationResult.template";

    private CertificateRepository mCertDB;
    private String mFormPath = null;
    private RequestQueue mQueue;
    private CAPublisherProcessor mPublisherProcessor;
    private String mRequestID = null;

    // http params
    public static final String SERIAL_NO = TOKEN_CERT_SERIAL;
    public static final String REASON_CODE = "reasonCode";
    public static final String CHALLENGE_PHRASE = "challengePhrase";

    // request attributes
    public static final String SERIALNO_ARRAY = "serialNoArray";

    public ChallengeRevocationServlet1() {
        super();
    }

    /**
     * Initialize the servlet. This servlet uses the file
     * revocationResult.template for the response
     *
     * @param sc servlet configuration, read from the web.xml file
     */
    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);

        CAEngine engine = CAEngine.getInstance();
        String authorityId = mAuthority.getId();

        mFormPath = "/" + authorityId + "/" + TPL_FILE;

        mTemplates.remove(CMSRequest.SUCCESS);
        if (mAuthority instanceof CertificateAuthority) {
            mCertDB = engine.getCertificateRepository();
        }

        mPublisherProcessor = engine.getPublisherProcessor();
        mQueue = engine.getRequestQueue();
    }

    /**
     * Process the HTTP request.
     * <ul>
     * <li>http.param REASON_CODE the revocation reason
     * <li>http.param b64eCertificate the base-64 encoded certificate to revoke
     * </ul>
     *
     * @param cmsReq the object holding the request and response information
     * @throws EBaseException
     */
    @Override
    protected void process(CMSRequest cmsReq) throws EBaseException {
        IArgBlock httpParams = cmsReq.getHttpParams();
        HttpServletRequest req = cmsReq.getHttpReq();
        HttpServletResponse resp = cmsReq.getHttpResp();

        CAEngine engine = CAEngine.getInstance();
        CertificateRepository cr = engine.getCertificateRepository();

        CMSTemplate form = null;
        Locale[] locale = new Locale[1];

        try {
            form = getTemplate(mFormPath, req, locale);
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERROR_DISPLAY_TEMPLATE"), e);
            throw new ECMSGWException(CMS.getLogMessage("CMSGW_ERROR_DISPLAY_TEMPLATE"), e);
        }

        ArgBlock header = new ArgBlock();
        ArgBlock ctx = new ArgBlock();
        CMSTemplateParams argSet = new CMSTemplateParams(header, ctx);

        // for audit log
        AuthToken authToken = authenticate(cmsReq);
        String authMgr = AuditFormat.NOAUTH;

        BigInteger[] serialNoArray = null;

        if (authToken != null) {
            serialNoArray = authToken.getInBigIntegerArray(SERIAL_NO);
        }
        // set revocation reason, default to unspecified if not set.
        int reasonCode =
                httpParams.getValueAsInt(REASON_CODE, 0);
        //        header.addIntegerValue("reason", reasonCode);

        String comments = req.getParameter(Request.REQUESTOR_COMMENTS);
        Date invalidityDate = null;
        String revokeAll = null;
        int totalRecordCount = (serialNoArray != null) ? serialNoArray.length : 0;
        int verifiedRecordCount = (serialNoArray != null) ? serialNoArray.length : 0;

        X509CertImpl[] certs = null;

        //for audit log.
        String initiative = null;

        if (mAuthMgr != null && mAuthMgr.equals(AuthSubsystem.CERTUSERDB_AUTHMGR_ID)) {
            // request is from agent
            if (authToken != null) {
                authMgr = authToken.getInString(AuthToken.TOKEN_AUTHMGR_INST_NAME);
                String agentID = authToken.getInString("userid");

                initiative = AuditFormat.FROMAGENT + " agentID: " + agentID +
                        " authenticated by " + authMgr;
            }
        } else {
            initiative = AuditFormat.FROMUSER;
        }

        AuthzToken authzToken = null;

        try {
            authzToken = authorize(mAclMethod, authToken,
                        mAuthzResourceName, "revoke");
        } catch (Exception e) {
            logger.warn(CMS.getLogMessage("ADMIN_SRVLT_AUTH_FAILURE", e.toString()), e);
        }

        if (authzToken == null) {
            cmsReq.setStatus(CMSRequest.UNAUTHORIZED);
            return;
        }

        if (serialNoArray != null && serialNoArray.length > 0) {
            if (mAuthority instanceof CertificateAuthority) {
                certs = new X509CertImpl[serialNoArray.length];

                for (int i = 0; i < serialNoArray.length; i++) {
                    certs[i] = cr.getX509Certificate(serialNoArray[i]);
                }
            }

            header.addIntegerValue("totalRecordCount", serialNoArray.length);
            header.addIntegerValue("verifiedRecordCount", serialNoArray.length);

            for (int i = 0; i < serialNoArray.length; i++) {
                ArgBlock rarg = new ArgBlock();

                rarg.addBigIntegerValue("serialNumber",
                        serialNoArray[i], 16);
                rarg.addStringValue("subject",
                        certs[i].getSubjectName().toString());
                rarg.addLongValue("validNotBefore",
                        certs[i].getNotBefore().getTime() / 1000);
                rarg.addLongValue("validNotAfter",
                        certs[i].getNotAfter().getTime() / 1000);
                //argSet.addRepeatRecord(rarg);
            }

            revokeAll = "(|(certRecordId=" + serialNoArray[0].toString() + "))";
            process(argSet, header, reasonCode, invalidityDate, initiative, req, resp,
                    verifiedRecordCount, revokeAll, totalRecordCount,
                    comments, locale[0]);
        } else {
            header.addIntegerValue("totalRecordCount", 0);
            header.addIntegerValue("verifiedRecordCount", 0);
        }

        try {
            ServletOutputStream out = resp.getOutputStream();

            if (serialNoArray == null) {
                logger.warn("ChallengeRevcationServlet1::process() - serialNoArray is null!");
                EBaseException ee = new EBaseException("No matched certificate is found");

                cmsReq.setError(ee);
                return;
            }

            if (serialNoArray.length == 0) {
                cmsReq.setStatus(CMSRequest.ERROR);
                EBaseException ee = new EBaseException("No matched certificate is found");

                cmsReq.setError(ee);
            } else {
                String xmlOutput = req.getParameter("xml");
                if (xmlOutput != null && xmlOutput.equals("true")) {
                    outputXML(resp, argSet);
                } else {
                    resp.setContentType("text/html");
                    form.renderOutput(out, argSet);
                    cmsReq.setStatus(CMSRequest.SUCCESS);
                }
            }
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("ADMIN_SRVLT_ERR_STREAM_TEMPLATE", e.toString()), e);
            throw new ECMSGWException(CMS.getLogMessage("CMSGW_ERROR_DISPLAY_TEMPLATE"), e);
        }
    }

    private void process(CMSTemplateParams argSet, IArgBlock header,
            int reason, Date invalidityDate,
            String initiative,
            HttpServletRequest req,
            HttpServletResponse resp,
            int verifiedRecordCount,
            String revokeAll,
            int totalRecordCount,
            String comments,
            Locale locale)
            throws EBaseException {

        CAEngine engine = CAEngine.getInstance();

        try {
            int count = 0;
            Vector<X509CertImpl> oldCertsV = new Vector<>();
            Vector<RevokedCertImpl> revCertImplsV = new Vector<>();

            // Construct a CRL reason code extension.
            RevocationReason revReason = RevocationReason.valueOf(reason);
            CRLReasonExtension crlReasonExtn = new CRLReasonExtension(revReason);

            // Construct a CRL invalidity date extension.
            InvalidityDateExtension invalidityDateExtn = null;

            if (invalidityDate != null) {
                invalidityDateExtn = new InvalidityDateExtension(invalidityDate);
            }

            // Construct a CRL extension for this request.
            CRLExtensions entryExtn = new CRLExtensions();

            if (crlReasonExtn != null) {
                entryExtn.set(crlReasonExtn.getName(), crlReasonExtn);
            }
            if (invalidityDateExtn != null) {
                entryExtn.set(invalidityDateExtn.getName(), invalidityDateExtn);
            }

            if (mAuthority instanceof CertificateAuthority) {
                CertRecordList list = mCertDB.findCertRecordsInList(revokeAll, null, totalRecordCount);
                Enumeration<CertRecord> e = list.getCertRecords(0, totalRecordCount - 1);

                while (e != null && e.hasMoreElements()) {
                    CertRecord rec = e.nextElement();
                    X509CertImpl cert = rec.getCertificate();
                    ArgBlock rarg = new ArgBlock();

                    rarg.addBigIntegerValue("serialNumber",
                            cert.getSerialNumber(), 16);

                    if (rec.getStatus().equals(CertRecord.STATUS_REVOKED)) {
                        rarg.addStringValue("error", "Certificate " +
                                cert.getSerialNumber().toString() +
                                " is already revoked.");
                    } else {
                        oldCertsV.addElement(cert);

                        RevokedCertImpl revCertImpl =
                                new RevokedCertImpl(cert.getSerialNumber(),
                                        new Date(), entryExtn);

                        revCertImplsV.addElement(revCertImpl);
                        count++;
                        rarg.addStringValue("error", null);
                    }
                    argSet.addRepeatRecord(rarg);
                }
            }

            header.addIntegerValue("totalRecordCount", count);

            X509CertImpl[] oldCerts = new X509CertImpl[count];
            RevokedCertImpl[] revCertImpls = new RevokedCertImpl[count];

            for (int i = 0; i < count; i++) {
                oldCerts[i] = oldCertsV.elementAt(i);
                revCertImpls[i] = revCertImplsV.elementAt(i);
            }

            CertRequestRepository requestRepository = engine.getCertRequestRepository();
            Request revReq = requestRepository.createRequest(Request.REVOCATION_REQUEST);

            revReq.setExtData(Request.CERT_INFO, revCertImpls);
            revReq.setExtData(Request.REQ_TYPE, Request.REVOCATION_REQUEST);
            revReq.setExtData(Request.REQUESTOR_TYPE, Request.REQUESTOR_AGENT);

            revReq.setExtData(Request.OLD_CERTS, oldCerts);
            if (comments != null) {
                revReq.setExtData(Request.REQUESTOR_COMMENTS, comments);
            }

            mQueue.processRequest(revReq);
            RequestStatus stat = revReq.getRequestStatus();

            if (stat == RequestStatus.COMPLETE) {
                // audit log the error
                Integer result = revReq.getExtDataInInteger(Request.RESULT);

                if (result.equals(Request.RES_ERROR)) {
                    String[] svcErrors =
                            revReq.getExtDataInStringArray(Request.SVCERRORS);

                    if (svcErrors != null && svcErrors.length > 0) {
                        for (int i = 0; i < svcErrors.length; i++) {
                            String err = svcErrors[i];

                            if (err != null) {
                                //cmsReq.setErrorDescription(err);
                                for (int j = 0; j < count; j++) {
                                    if (oldCerts[j] != null) {
                                        logger.info(
                                                AuditFormat.DOREVOKEFORMAT,
                                                revReq.getRequestId(),
                                                initiative,
                                                "completed with error: " + err,
                                                oldCerts[j].getSubjectName(),
                                                oldCerts[j].getSerialNumber().toString(16),
                                                RevocationReason.valueOf(reason)
                                        );
                                    }
                                }
                            }
                        }
                    }
                    return;
                }

                // audit log the success.
                for (int j = 0; j < count; j++) {
                    if (oldCerts[j] != null) {
                        logger.info(
                                AuditFormat.DOREVOKEFORMAT,
                                revReq.getRequestId(),
                                initiative,
                                "completed",
                                oldCerts[j].getSubjectName(),
                                oldCerts[j].getSerialNumber().toString(16),
                                RevocationReason.valueOf(reason)
                        );
                    }
                }

                header.addStringValue("revoked", "yes");

                Integer updateCRLResult =
                        revReq.getExtDataInInteger(Request.CRL_UPDATE_STATUS);

                if (updateCRLResult != null) {
                    header.addStringValue("updateCRL", "yes");
                    if (updateCRLResult.equals(Request.RES_SUCCESS)) {
                        header.addStringValue("updateCRLSuccess", "yes");
                    } else {
                        header.addStringValue("updateCRLSuccess", "no");
                        String crlError =
                                revReq.getExtDataInString(Request.CRL_UPDATE_ERROR);

                        if (crlError != null)
                            header.addStringValue("updateCRLError",
                                    crlError);
                    }
                    // let known crl publishing status too.
                    Integer publishCRLResult =
                            revReq.getExtDataInInteger(Request.CRL_PUBLISH_STATUS);

                    if (publishCRLResult != null) {
                        if (publishCRLResult.equals(Request.RES_SUCCESS)) {
                            header.addStringValue("publishCRLSuccess", "yes");
                        } else {
                            header.addStringValue("publishCRLSuccess", "no");
                            String publError =
                                    revReq.getExtDataInString(Request.CRL_PUBLISH_ERROR);

                            if (publError != null)
                                header.addStringValue("publishCRLError",
                                        publError);
                        }
                    }
                }
                if (mAuthority instanceof CertificateAuthority) {
                    // let known update and publish status of all crls.
                    for (CRLIssuingPoint crl : engine.getCRLIssuingPoints()) {
                        String crlId = crl.getId();

                        if (crlId.equals(CertificateAuthority.PROP_MASTER_CRL))
                            continue;
                        String updateStatusStr = crl.getCrlUpdateStatusStr();
                        Integer updateResult = revReq.getExtDataInInteger(updateStatusStr);

                        if (updateResult != null) {
                            if (updateResult.equals(Request.RES_SUCCESS)) {
                                logger.debug("ChallengeRevcationServlet1: "
                                        + CMS.getLogMessage("ADMIN_SRVLT_ADDING_HEADER",
                                                updateStatusStr));
                                header.addStringValue(updateStatusStr, "yes");
                            } else {
                                String updateErrorStr = crl.getCrlUpdateErrorStr();

                                logger.debug("ChallengeRevcationServlet1: "
                                        + CMS.getLogMessage("ADMIN_SRVLT_ADDING_HEADER_NO",
                                                updateStatusStr));
                                header.addStringValue(updateStatusStr, "no");
                                String error =
                                        revReq.getExtDataInString(updateErrorStr);

                                if (error != null)
                                    header.addStringValue(updateErrorStr,
                                            error);
                            }
                            String publishStatusStr = crl.getCrlPublishStatusStr();
                            Integer publishResult =
                                    revReq.getExtDataInInteger(publishStatusStr);

                            if (publishResult == null)
                                continue;
                            if (publishResult.equals(Request.RES_SUCCESS)) {
                                header.addStringValue(publishStatusStr, "yes");
                            } else {
                                String publishErrorStr =
                                        crl.getCrlPublishErrorStr();

                                header.addStringValue(publishStatusStr, "no");
                                String error =
                                        revReq.getExtDataInString(publishErrorStr);

                                if (error != null)
                                    header.addStringValue(
                                            publishErrorStr, error);
                            }
                        }
                    }
                }

                if (mPublisherProcessor != null && mPublisherProcessor.ldapEnabled()) {
                    header.addStringValue("dirEnabled", "yes");
                    Integer[] ldapPublishStatus =
                            revReq.getExtDataInIntegerArray("ldapPublishStatus");
                    int certsToUpdate = 0;
                    int certsUpdated = 0;

                    if (ldapPublishStatus != null) {
                        certsToUpdate = ldapPublishStatus.length;
                        for (int i = 0; i < certsToUpdate; i++) {
                            if (ldapPublishStatus[i] == Request.RES_SUCCESS) {
                                certsUpdated++;
                            }
                        }
                    }
                    header.addIntegerValue("certsUpdated", certsUpdated);
                    header.addIntegerValue("certsToUpdate", certsToUpdate);

                    // add crl publishing status.
                    String publError =
                            revReq.getExtDataInString(Request.CRL_PUBLISH_ERROR);

                    if (publError != null) {
                        header.addStringValue("crlPublishError",
                                publError);
                    }
                } else {
                    header.addStringValue("dirEnabled", "no");
                }
                header.addStringValue("error", null);

            } else if (stat == RequestStatus.PENDING) {
                header.addStringValue("error", "Request Pending");
                header.addStringValue("revoked", "pending");
                // audit log the pending
                for (int j = 0; j < count; j++) {
                    if (oldCerts[j] != null) {
                        logger.info(
                                AuditFormat.DOREVOKEFORMAT,
                                revReq.getRequestId(),
                                initiative,
                                "pending",
                                oldCerts[j].getSubjectName(),
                                oldCerts[j].getSerialNumber().toString(16),
                                RevocationReason.valueOf(reason)
                        );
                    }
                }

            } else {
                Vector<String> errors = revReq.getExtDataInStringVector(Request.ERRORS);
                StringBuffer errorStr = new StringBuffer();

                if (errors != null && errors.size() > 0) {
                    for (int ii = 0; ii < errors.size(); ii++) {
                        errorStr.append(errors.elementAt(ii));
                    }
                }
                header.addStringValue("error", errorStr.toString());
                header.addStringValue("revoked", "no");
                // audit log the error
                for (int j = 0; j < count; j++) {
                    if (oldCerts[j] != null) {
                        logger.info(
                                AuditFormat.DOREVOKEFORMAT,
                                revReq.getRequestId(),
                                initiative,
                                stat,
                                oldCerts[j].getSubjectName(),
                                oldCerts[j].getSerialNumber().toString(16),
                                RevocationReason.valueOf(reason)
                        );
                    }
                }
            }

        } catch (EBaseException e) {
            logger.error("ChallengeRevocationServlet1: " + e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERROR_MARKING_CERT_REVOKED", e.toString()), e);
            throw new ECMSGWException(CMS.getLogMessage("CMSGW_ERROR_MARKING_CERT_REVOKED"), e);
        } catch (Exception e) {
            logger.warn("ChallengeRevocationServlet1: " + e.getMessage(), e);
        }

        return;
    }
}
