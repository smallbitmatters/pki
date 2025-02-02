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
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dogtagpki.server.ca.ICertificateAuthority;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.cms.servlet.base.CMSServlet;
import com.netscape.cms.servlet.common.CMSRequest;
import com.netscape.cms.servlet.common.CMSTemplate;
import com.netscape.cms.servlet.common.CMSTemplateParams;
import com.netscape.cms.servlet.common.ECMSGWException;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ArgBlock;
import com.netscape.cmscore.base.ConfigStore;

/**
 * Redirect a request to the Master. This servlet is used in
 * a clone when a requested service (such as CRL) is not available.
 * It redirects the user to the master.
 *
 * @version $Revision$, $Date$
 */
public class CloneRedirect extends CMSServlet {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CloneRedirect.class);
    private static final long serialVersionUID = 3217967115281965166L;
    private final static String PROP_REDIRECT_URL = "masterURL";
    private final static String TPL_FILE = "cloneRedirect.template";

    private String mNewUrl = null;
    private String mFormPath = null;

    private ICertificateAuthority mCA = null;

    /**
     * Constructs CloneRedirect servlet.
     */
    public CloneRedirect() {
        super();

    }

    /**
     * Initialize the servlet.
     *
     * @param sc servlet configuration, read from the web.xml file
     */
    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);
        mFormPath = "/" + mAuthority.getId() + "/" + TPL_FILE;

        if (mAuthority instanceof ICertificateAuthority) {
            mCA = (ICertificateAuthority) mAuthority;
            ConfigStore authConfig = mCA.getConfigStore();

            if (authConfig != null) {
                try {
                    mNewUrl = authConfig.getString(PROP_REDIRECT_URL,
                                "*** master URL unavailable, check your configuration ***");
                } catch (EBaseException e) {
                    // do nothing
                }
            }
        }

        if (mAuthority instanceof ICertificateAuthority)
            mCA = (ICertificateAuthority) mAuthority;

        // override success to do output with our own template.
        mTemplates.remove(CMSRequest.SUCCESS);
    }

    /**
     * Serves HTTP request.
     */
    @Override
    public void process(CMSRequest cmsReq) throws EBaseException {
        HttpServletRequest req = cmsReq.getHttpReq();
        HttpServletResponse resp = cmsReq.getHttpResp();

        ArgBlock header = new ArgBlock();
        ArgBlock fixed = new ArgBlock();
        CMSTemplateParams argSet = new CMSTemplateParams(header, fixed);

        CMSTemplate form = null;
        Locale[] locale = new Locale[1];

        try {
            form = getTemplate(mFormPath, req, locale);
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERROR_DISPLAY_TEMPLATE"), e);
            throw new ECMSGWException(CMS.getLogMessage("CMSGW_ERROR_DISPLAY_TEMPLATE"), e);
        }

        logger.debug("CloneRedirect: " + CMS.getLogMessage("ADMIN_SRVLT_ADD_MASTER_URL", mNewUrl));
        header.addStringValue("masterURL", mNewUrl);
        try {
            ServletOutputStream out = resp.getOutputStream();

            String xmlOutput = req.getParameter("xml");
            if (xmlOutput != null && xmlOutput.equals("true")) {
                outputXML(resp, argSet);
            } else {
                resp.setContentType("text/html");
                form.renderOutput(out, argSet);
                cmsReq.setStatus(CMSRequest.SUCCESS);
            }
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("ADMIN_SRVLT_ERR_STREAM_TEMPLATE", e.toString()), e);
            throw new ECMSGWException(CMS.getLogMessage("CMSGW_ERROR_DISPLAY_TEMPLATE"), e);
        }
    }
}
