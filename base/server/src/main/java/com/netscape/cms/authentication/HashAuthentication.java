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
package com.netscape.cms.authentication;

// ldap java sdk
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.dogtagpki.server.authentication.AuthManager;
import org.dogtagpki.server.authentication.AuthManagerConfig;
import org.dogtagpki.server.authentication.AuthToken;
import org.mozilla.jss.netscape.security.util.Utils;

import com.netscape.certsrv.authentication.AuthCredentials;
import com.netscape.certsrv.authentication.EAuthException;
import com.netscape.certsrv.authentication.EInvalidCredentials;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IExtendedPluginInfo;
import com.netscape.cmscore.apps.CMS;

/**
 * Hash uid/pwd directory based authentication manager
 * <P>
 *
 * @version $Revision$, $Date$
 */
public class HashAuthentication implements AuthManager, IExtendedPluginInfo {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HashAuthentication.class);

    public static final String SALT = "lala123";
    public static final String CRED_UID = "uid";
    public static final String CRED_FINGERPRINT = "fingerprint";
    public static final String CRED_PAGEID = "pageID";
    public static final String CRED_HOST = "hostname";
    protected static String[] mRequiredCreds = { CRED_UID,
            CRED_PAGEID, CRED_FINGERPRINT, CRED_HOST };
    public static final long DEFAULT_TIMEOUT = 600000;

    private MessageDigest mSHADigest = null;
    private Hashtable<String, AuthToken> mData = null;
    private AuthManagerConfig mConfig;
    private String mName = null;
    private String mImplName = null;
    private static Vector<String> mExtendedPluginInfo = null;
    private HashAuthData mHosts = null;

    static String[] mConfigParams =
            new String[] {};

    static {
        mExtendedPluginInfo = new Vector<>();
        mExtendedPluginInfo.add(IExtendedPluginInfo.HELP_TEXT +
                ";Authenticate the username and password provided " +
                "by the user against an LDAP directory. Works with the " +
                "Dir Based Enrollment HTML form");
        mExtendedPluginInfo.add(IExtendedPluginInfo.HELP_TOKEN +
                ";configuration-authrules-uidpwddirauth");
    };

    /**
     * Default constructor, initialization must follow.
     */
    public HashAuthentication() {
    }

    @Override
    public void init(String name, String implName, AuthManagerConfig config)
            throws EBaseException {
        mName = name;
        mImplName = implName;
        mConfig = config;
        mData = new Hashtable<>();
        mHosts = new HashAuthData();

        try {
            mSHADigest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new EAuthException(CMS.getUserMessage("CMS_AUTHENTICATION_INTERNAL_ERROR", e.getMessage()));
        }

    }

    public AuthToken getAuthToken(String key) {
        return mData.remove(key);
    }

    public void addAuthToken(String pageID, AuthToken token) {
        mData.put(pageID, token);
    }

    public void deleteToken(String pageID) {
        mData.remove(pageID);
    }

    public HashAuthData getData() {
        return mHosts;
    }

    public void createEntry(String host, String dn, long timeout,
            String secret, long lastLogin) {
        Vector<Object> v = new Vector<>();

        v.addElement(dn);
        v.addElement(Long.valueOf(timeout));
        v.addElement(secret);
        v.addElement(Long.valueOf(lastLogin));
        mHosts.put(host, v);
    }

    public void disable(String hostname) {
        mHosts.remove(hostname);
    }

    public String getAgentName(String hostname) {
        return mHosts.getAgentName(hostname);
    }

    public void setAgentName(String hostname, String agentName) {
        mHosts.setAgentName(hostname, agentName);
    }

    public boolean isEnable(String hostname) {
        return mHosts.containsKey(hostname);
    }

    public long getTimeout(String hostname) {
        return mHosts.getTimeout(hostname);
    }

    public void setTimeout(String hostname, long timeout) {
        mHosts.setTimeout(hostname, timeout);
    }

    public String getSecret(String hostname) {
        return mHosts.getSecret(hostname);
    }

    public void setSecret(String hostname, String secret) {
        mHosts.setSecret(hostname, secret);
    }

    public long getLastLogin(String hostname) {
        return mHosts.getLastLogin(hostname);
    }

    public void setLastLogin(String hostname, long lastlogin) {
        mHosts.setLastLogin(hostname, lastlogin);
    }

    public long getPageID() {
        Date date = new Date();

        return date.getTime();
    }

    public boolean validFingerprint(String host, String pageID, String uid, String fingerprint) {
        String val = hashFingerprint(host, pageID, uid);

        if (val.equals(fingerprint))
            return true;
        return false;
    }

    public Enumeration<String> getHosts() {
        return mHosts.keys();
    }

    public String hashFingerprint(String host, String pageID, String uid) {
        byte[] hash =
                mSHADigest.digest((SALT + pageID + getSecret(host) + uid).getBytes());
        String b64E = Utils.base64encode(hash, true);

        return "{SHA}" + b64E;
    }

    @Override
    public void shutdown() {
    }

    /**
     * Authenticates a user based on uid, pwd in the directory.
     *
     * @param authCreds The authentication credentials.
     * @return The user's ldap entry dn.
     * @exception EInvalidCredentials If the uid and password are not valid
     * @exception EBaseException If an internal error occurs.
     */
    @Override
    public AuthToken authenticate(AuthCredentials authCreds)
            throws EBaseException {
        AuthToken token = new AuthToken(this);
        String fingerprint = (String) authCreds.get(CRED_FINGERPRINT);
        String pageID = (String) authCreds.get(CRED_PAGEID);
        String uid = (String) authCreds.get(CRED_UID);
        String host = (String) authCreds.get(CRED_HOST);

        if (fingerprint.equals("") ||
                !validFingerprint(host, pageID, uid, fingerprint)) {
            logger.error("HashAuthentication: " + CMS.getLogMessage("CMS_AUTH_INVALID_FINGER_PRINT"));
            throw new EAuthException("Invalid Fingerprint");
        }

        // set uid in the token.
        token.set(CRED_UID, uid);

        return token;
    }

    /**
     * Returns array of required credentials for this authentication manager.
     *
     * @return Array of required credentials.
     */
    @Override
    public String[] getRequiredCreds() {
        return mRequiredCreds;
    }

    /**
     * Gets the configuration substore used by this authentication manager
     *
     * @return configuration store
     */
    @Override
    public AuthManagerConfig getConfigStore() {
        return mConfig;
    }

    /**
     * gets the name of this authentication manager instance
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * gets the plugin name of this authentication manager.
     */
    @Override
    public String getImplName() {
        return mImplName;
    }

    @Override
    public String[] getExtendedPluginInfo() {
        return Utils.getStringArrayFromVector(mExtendedPluginInfo);
    }

    /**
     * Returns a list of configuration parameter names.
     * The list is passed to the configuration console so instances of
     * this implementation can be configured through the console.
     *
     * @return String array of configuration parameter names.
     */
    @Override
    public String[] getConfigParams() {
        return (mConfigParams);
    }
}
