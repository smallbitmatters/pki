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
package com.netscape.certsrv.authentication;

import java.util.Enumeration;
import java.util.Hashtable;

import com.netscape.certsrv.base.IArgBlock;
import com.netscape.certsrv.base.IAttrSet;

/**
 * Authentication Credentials as input to the authMgr. It contains all the
 * information required for authentication in the authMgr.
 */
public class AuthCredentials implements IAttrSet {

    private static final long serialVersionUID = 5862936214648594328L;
    private Hashtable<String, Object> authCreds = null;
    private IArgBlock argblk = null;

    /**
     * Constructor
     */
    public AuthCredentials() {
        authCreds = new Hashtable<>();
    }

    /**
     * Sets an authentication credential with credential name and the credential object
     *
     * @param name credential name
     * @param cred credential object
     */
    @Override
    public void set(String name, Object cred) throws EAuthException {

        if (name == null) {
            throw new EAuthException("Missing credential name");
        }

        if (cred == null) {
            throw new EAuthException("Missing credential: " + name);
        }

        authCreds.put(name, cred);
    }

    /**
     * Returns the credential to which the specified name is mapped in this
     * credential set
     *
     * @param name credential name
     * @return the authentication credential for the given name
     */
    @Override
    public Object get(String name) {
        return authCreds.get(name);
    }

    /**
     * Removes the name and its corresponding credential from this
     * credential set. This method does nothing if the named
     * credential is not in the credential set.
     *
     * @param name credential name
     */
    @Override
    public void delete(String name) {
        authCreds.remove(name);
    }

    /**
     * Returns an enumeration of the credential names in this credential
     * set. Use the Enumeration methods on the returned object to
     * fetch the elements sequentially.
     *
     * @return an enumeration of the names in this credential set
     */
    @Override
    public Enumeration<String> getElements() {
        return authCreds.keys();
    }

    /**
     * Set argblock.
     *
     * @param blk argblock
     */
    public void setArgBlock(IArgBlock blk) {
        argblk = blk;
    }

    /**
     * Returns argblock.
     *
     * @return argblock.
     */
    public IArgBlock getArgBlock() {
        return argblk;
    }
}
