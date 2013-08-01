//--- BEGIN COPYRIGHT BLOCK ---
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; version 2 of the License.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License along
//with this program; if not, write to the Free Software Foundation, Inc.,
//51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//(C) 2012 Red Hat, Inc.
//All rights reserved.
//--- END COPYRIGHT BLOCK ---
package com.netscape.cms.authorization;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.Principal;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.spi.Failure;

import com.netscape.certsrv.acls.ACLMapping;
import com.netscape.certsrv.apps.CMS;
import com.netscape.certsrv.authentication.IAuthToken;
import com.netscape.certsrv.authorization.AuthzToken;
import com.netscape.certsrv.authorization.EAuthzAccessDenied;
import com.netscape.certsrv.authorization.IAuthzSubsystem;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.ForbiddenException;
import com.netscape.cms.realm.PKIPrincipal;

/**
 * @author Endi S. Dewata
 */
@Provider
public class ACLInterceptor implements ContainerRequestFilter {

    Properties authProperties;

    @Context
    ServletContext servletContext;

    @Context
    SecurityContext securityContext;

    public synchronized void loadAuthProperties() throws IOException {

        if (authProperties != null)
            return;

        URL url = servletContext.getResource("/WEB-INF/auth.properties");
        authProperties = new Properties();
        authProperties.load(url.openStream());
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        ResourceMethodInvoker methodInvoker = (ResourceMethodInvoker) requestContext
                .getProperty("org.jboss.resteasy.core.ResourceMethodInvoker");
        Method method = methodInvoker.getMethod();

        ACLMapping aclMapping = method.getAnnotation(ACLMapping.class);

        // If not available, get ACL mapping for the class.
        if (aclMapping == null) {
            Class<?> clazz = method.getDeclaringClass();
            aclMapping = clazz.getAnnotation(ACLMapping.class);
        }

        // If still not available, it's unprotected, allow request.
        if (aclMapping == null) {
            CMS.debug("ACLInterceptor: No ACL mapping.");
            return;
        }

        Principal principal = securityContext.getUserPrincipal();

        // If unauthenticated, reject request.
        if (principal == null) {
            CMS.debug("ACLInterceptor: No user principal provided.");
            throw new ForbiddenException("No user principal provided.");
        }

        // If unrecognized principal, reject request.
        if (!(principal instanceof PKIPrincipal)) {
            CMS.debug("ACLInterceptor: Invalid user principal.");
            throw new ForbiddenException("Invalid user principal.");
        }

        PKIPrincipal pkiPrincipal = (PKIPrincipal) principal;
        IAuthToken authToken = pkiPrincipal.getAuthToken();

        // If missing auth token, reject request.
        if (authToken == null) {
            CMS.debug("ACLInterceptor: No authorization token present.");
            throw new ForbiddenException("No authorization token present.");
        }

        try {
            loadAuthProperties();

            String name = aclMapping.value();
            String value = authProperties.getProperty(name);

            // If no property defined, allow request.
            if (value == null) {
                CMS.debug("ACLInterceptor: No ACL configuration.");
                return;
            }

            String values[] = value.split(",");

            // If invalid mapping, reject request.
            if (values.length != 2) {
                CMS.debug("ACLInterceptor: Invalid ACL mapping.");
                throw new ForbiddenException("Invalid ACL mapping.");
            }

            // Check authorization.
            IAuthzSubsystem mAuthz = (IAuthzSubsystem) CMS.getSubsystem(CMS.SUBSYSTEM_AUTHZ);
            AuthzToken authzToken = mAuthz.authorize(
                    "DirAclAuthz",
                    authToken,
                    values[0], // resource
                    values[1]); // operation

            // If not authorized, reject request.
            if (authzToken == null) {
                CMS.debug("ACLInterceptor: No authorization token present.");
                throw new ForbiddenException("No authorization token present.");
            }

        } catch (EAuthzAccessDenied e) {
            CMS.debug("ACLInterceptor: " + e.getMessage());
            throw new ForbiddenException(e.toString());

        } catch (IOException | EBaseException e) {
            e.printStackTrace();
            throw new Failure(e);
        }

        // Allow request.
        return;
    }
}
