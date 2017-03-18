package org.kontinuity.catapult.service.keycloak.impl;

import org.junit.Assert;
import org.junit.Test;

public class KeycloakServiceTest {
    @Test
    public void testBuildUrl() {
        String url = KeycloakServiceImpl.buildURL("http://sso.prod-preview.openshift.io", "fabric8", "github");
        Assert.assertEquals("http://sso.prod-preview.openshift.io/auth/realms/fabric8/broker/github/token", url);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRequestURL() {
        KeycloakServiceImpl service = new KeycloakServiceImpl("foo","realm");
        service.getOpenShiftToken("anything");
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidRequest() {
        //Service should not be available
        KeycloakServiceImpl service = new KeycloakServiceImpl("http://localhost:5555","realm");
        service.getOpenShiftToken("token");
        Assert.fail("Should have thrown IllegalStateException");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTokenOpenshift() {
        String token = "token";
        KeycloakServiceImpl service = new KeycloakServiceImpl();
        Assert.assertNotNull(service.getOpenShiftToken(token));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTokenGithub() {
        String token = "InvalidToken";
        KeycloakServiceImpl service = new KeycloakServiceImpl();
        Assert.assertNotNull(service.getGithubToken(token));
    }

    @Test
    public void testValidTokenGithub() {
        String token = KeycloakTestCredentials.getToken();
        KeycloakServiceImpl service = new KeycloakServiceImpl();
        Assert.assertNotNull(service.getGithubToken(token));
    }

    @Test
    public void testValidTokenOpenshift() {
        String token = KeycloakTestCredentials.getToken();
        KeycloakServiceImpl service = new KeycloakServiceImpl();
        Assert.assertNotNull(service.getOpenShiftToken(token));
    }

}
