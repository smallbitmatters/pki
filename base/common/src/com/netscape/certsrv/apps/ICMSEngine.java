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
package com.netscape.certsrv.apps;

import java.util.Date;
import java.util.Enumeration;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.ISubsystem;

/**
 * This interface represents the CMS core framework. The
 * framework contains a set of services that provide
 * the foundation of a security application.
 * <p>
 * The engine implementation is loaded by CMS at startup. It is responsible for starting up all the related subsystems.
 * <p>
 *
 * @version $Revision$, $Date$
 */
public interface ICMSEngine extends ISubsystem {

    /**
     * Gets this ID .
     *
     * @return CMS engine identifier
     */
    public String getId();

    /**
     * Sets the identifier of this subsystem. Should never be called.
     * Returns error.
     *
     * @param id CMS engine identifier
     */
    public void setId(String id) throws EBaseException;

    public void reinit(String id) throws EBaseException;

    public int getCSState();

    public void setCSState(int mode);

    /**
     * Returns a server wide system time. Plugins should call
     * this method to retrieve system time.
     *
     * @return current time
     */
    public Date getCurrentDate();

    /**
     * Returns the names of all the registered subsystems.
     *
     * @return a list of string-based subsystem names
     */
    public Enumeration<String> getSubsystemNames();

    /**
     * Returns all the registered subsystems.
     *
     * @return a list of ISubsystem-based subsystems
     */
    public Enumeration<ISubsystem> getSubsystems();

    /**
     * Set whether the given subsystem is enabled.
     *
     * @param id The subsystem ID.
     * @param enabled Whether the subsystem is enabled
     */
    public void setSubsystemEnabled(String id, boolean enabled)
        throws EBaseException;

    /**
     * Puts a message into the debug file.
     *
     * @param msg debugging message
     */
    public void debug(String msg);

    /**
     * Blocks all new incoming requests.
     */
    public void disableRequests();

    /**
     * Terminates all requests that are currently in process.
     */
    public void terminateRequests();

    /**
     * Checks to ensure that all new incoming requests have been blocked.
     * This method is used for reentrancy protection.
     * <P>
     *
     * @return true or false
     */
    public boolean areRequestsDisabled();
}
