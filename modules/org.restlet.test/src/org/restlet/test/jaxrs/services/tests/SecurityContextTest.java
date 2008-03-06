/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package org.restlet.test.jaxrs.services.tests;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Form;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.jaxrs.AllowAllAccess;
import org.restlet.ext.jaxrs.AccessControl;
import org.restlet.ext.jaxrs.ForbidAllAccess;
import org.restlet.resource.Representation;
import org.restlet.test.jaxrs.server.RestletServerWrapper;
import org.restlet.test.jaxrs.server.ServerWrapper;
import org.restlet.test.jaxrs.services.resources.SecurityContextService;

/**
 * This test class checks if the Request.evaluatePreconditions methods works
 * fine.
 * 
 * @author Stephan Koops
 * @see SecurityContextService
 */
public class SecurityContextTest extends JaxRsTestCase {
    private static final Class<SecurityContextService> SEC_CONT_SERV = SecurityContextService.class;

    @Override
    protected Class<?> getRootResourceClass() {
        return SEC_CONT_SERV;
    }

    /**
     * @param accessControl
     * @return true, if it could be set, or false if not.
     */
    private boolean setAccessControl(AccessControl accessControl) {
        ServerWrapper serverWrapper = getServerWrapper();
        if (serverWrapper instanceof RestletServerWrapper) {
            RestletServerWrapper restletServerWrapper = ((RestletServerWrapper) serverWrapper);
            restletServerWrapper.setAccessControl(accessControl);
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldStartServerInSetUp() {
        return false;
    }

    /**
     * @param accessControl
     * @throws Exception
     */
    private boolean startServer(AccessControl accessControl) throws Exception {
        if (!setAccessControl(accessControl))
            return false;
        startServer();
        return true;
    }

    /**
     * Allow access, but forbid all rules
     * 
     * @throws Exception
     */
    public void test2() throws Exception {
        if (!startServer(ForbidAllAccess.getInstance()))
            return;
        Response response = get();
        assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, response.getStatus());

        response = getAuth(null, "ydfsdf", "ydf");
        assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, response.getStatus());
    }

    public void test3() throws Exception {
        if (!startServer(ForbidAllAccess.getInstance()))
            return;
        Response response = getAuth(null, "admin", "adminPW");
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, response.getStatus());
    }

    public void test4() throws Exception {
        if (!startServer(ForbidAllAccess.getInstance()))
            return;
        Response response = post(null, new Form().getWebRepresentation(),
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "alice",
                        "alicesSecret"));
        sysOutEntityIfError(response);
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, response.getStatus());
    }

    public void test5() throws Exception {
        if (!startServer(ForbidAllAccess.getInstance()))
            return;
        Representation postEntity = new Form("abc=def").getWebRepresentation();
        ChallengeResponse cr = new ChallengeResponse(
                ChallengeScheme.HTTP_BASIC, "bob", "bobsSecret");
        Response response = post(null, postEntity, cr);
        sysOutEntityIfError(response);
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, response.getStatus());
    }

    public void testAllowAll() throws Exception {
        startServer(); // no authorization
        Response response = get();
        assertEquals(Status.SUCCESS_OK, response.getStatus());

        response = post(new Form("abc=def").getWebRepresentation());
        sysOutEntityIfError(response);
        assertEquals(Status.SUCCESS_CREATED, response.getStatus());
        Reference expecretLocation = createReference(SEC_CONT_SERV, null);
        assertTrue("The location must start with " + expecretLocation
                + "; it is " + response.getLocationRef(), response
                .getLocationRef().toString().startsWith(
                        expecretLocation.toString()));
    }

    public void testAuthenticationSchemeBasic() throws Exception {
        if (!startServer(AllowAllAccess.getInstance()))
            return;
        ChallengeResponse cr = new ChallengeResponse(
                ChallengeScheme.HTTP_BASIC, "bob", "bobsSecret");
        Response response = get("authenticationScheme", cr);
        assertEquals(Status.SUCCESS_OK, response.getStatus());
        String entity = response.getEntity().getText();
        assertEquals(SecurityContext.BASIC_AUTH, entity);
    }

    // TODO create extra TestCase: DigestAuth does not work 
    public void _testAuthenticationSchemeDigest() throws Exception {
        if (!setAccessControl(AllowAllAccess.getInstance()))
            return;
        startServer(ChallengeScheme.HTTP_DIGEST);
        ChallengeResponse cr = new ChallengeResponse(
                ChallengeScheme.HTTP_DIGEST, "alice", "alicesSecret");
        Response response = get("authenticationScheme", cr);
        assertEquals(Status.SUCCESS_OK, response.getStatus());
        String entity = response.getEntity().getText();
        assertEquals(SecurityContext.DIGEST_AUTH, entity);
    }

    public void testForbidAll() throws Exception {
        if (!startServer(ForbidAllAccess.getInstance()))
            return;
        Response response = get();
        assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, response.getStatus());

        response = post(new Form("abc=def").getWebRepresentation());
        assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, response.getStatus());
    }

    /**
     * @throws Exception
     */
    public void testNoRoles() throws Exception {
        AccessControl exampleAuthorizator = new AccessControl() {
            /**
             * @return true, if the role name and the username starts with the
             *         same char.
             * @see AccessControl#isUserInRole(String)
             */
            public boolean isUserInRole(Principal principal, String role) {
                if (principal == null)
                    throw new IllegalArgumentException("No principal given");
                if (role == null)
                    throw new IllegalArgumentException("No role given");
                if (role.charAt(0) == principal.getName().charAt(0))
                    return true;
                return false;
            }
        };
        if (!startServer(exampleAuthorizator))
            return;
        Response response = getAuth(null, "fsdf", "xyz");
        assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, response.getStatus());

        response = getAuth(null, "fsdf", "baj");
        assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, response.getStatus());

        response = getAuth(null, "alice", "alicesSecret");
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, response.getStatus());

        response = getAuth(null, "bob", "bobsSecret");
        assertEquals(Status.SUCCESS_OK, response.getStatus());

        ChallengeResponse cr;
        cr = new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "bob",
                "bobsSecret");
        response = post(null, new Form("abc=def").getWebRepresentation(), cr);
        sysOutEntityIfError(response);
        assertEquals(Status.SUCCESS_CREATED, response.getStatus());

        response = accessServer(Method.PUT, SEC_CONT_SERV, null, null, cr);
        assertEquals(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, response
                .getStatus());
    }

    // hope that Restlet Request.isConfidential(); works right with HTTPS

    public void testSecure() throws Exception {
        startServer();
        Response response = get("secure");
        assertEquals(Status.CLIENT_ERROR_NOT_FOUND, response.getStatus());
    }

    public void testUserPrincipalNotAuth() throws Exception {
        startServer();
        Response response = get("userPrincipal");
        assertEquals(Status.SUCCESS_OK, response.getStatus());
        String entity = response.getEntity().getText();
        assertEquals("-", entity);
    }

    public void testUserPrincipalAuth() throws Exception {
        if (!startServer(AllowAllAccess.getInstance()))
            return;
        Response response = getAuth("userPrincipal", "alice", "alicesSecret");
        assertEquals(Status.SUCCESS_OK, response.getStatus());
        String entity = response.getEntity().getText();
        assertEquals("alice", entity);

        response = getAuth("userPrincipal", "bob", "bobsSecret");
        assertEquals(Status.SUCCESS_OK, response.getStatus());
        entity = response.getEntity().getText();
        assertEquals("bob", entity);
    }
}