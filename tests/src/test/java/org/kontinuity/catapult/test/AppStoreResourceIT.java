package org.kontinuity.catapult.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kontinuity.catapult.base.test.EnvironmentVariableController;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Tests for the {@link org.kontinuity.catapult.web.api.AppStoreResource}
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
@RunWith(Arquillian.class)
public class AppStoreResourceIT {

   private static final Logger log = Logger.getLogger(AppStoreResourceIT.class.getName());

   private static final String EXPECTED_DEFAULT_APPSTORE_URL = "https://raw.githubusercontent.com/redhat-kontinuity/appstore/master/appstore.json";

   private static final String NAME_SYSPROP_ENVVAR_APPSTORE_URL = "CATAPULT_APPSTORE_URL";

   private String appstoreEnvVarValueBefore;
   private String appstoreSyspropValueBefore;

   private URL appStoreUrl;

   @ArquillianResource
   private URL deploymentUrl;

   @Deployment
   public static WebArchive getRealDeployment() {
      return Deployments.getMavenBuiltWar().
              addClass(EnvironmentVariableController.class);
   }

   @Before
   public void initAppStoreUrl() {
      try {
         appStoreUrl = new URL(deploymentUrl, "api/appstore/applications");
         log.info("Using appStoreUrl: " + appStoreUrl.toExternalForm());
      } catch (final MalformedURLException murle) {
         throw new RuntimeException("Invalid URL for appstore", murle);
      }
   }

   @Before
   public void clearEnvState() {
      appstoreEnvVarValueBefore = System.getenv(NAME_SYSPROP_ENVVAR_APPSTORE_URL);
      EnvironmentVariableController.setEnv(NAME_SYSPROP_ENVVAR_APPSTORE_URL, "");
      appstoreSyspropValueBefore = System.getProperty(NAME_SYSPROP_ENVVAR_APPSTORE_URL);
      System.setProperty(NAME_SYSPROP_ENVVAR_APPSTORE_URL, "");
   }

   @After
   public void resetEnvState() {
      EnvironmentVariableController.setEnv(NAME_SYSPROP_ENVVAR_APPSTORE_URL,
              appstoreEnvVarValueBefore == null ? "" : appstoreEnvVarValueBefore);
      System.setProperty(NAME_SYSPROP_ENVVAR_APPSTORE_URL,
              appstoreSyspropValueBefore == null ? "" : appstoreSyspropValueBefore);
   }

   @Test
   public void defaultAppstoreUrl() {
      final Response response = ClientBuilder.newClient().target(
              appStoreUrl.toExternalForm()).request().get();
      final String currentUrl = response.getLocation().toString();
      log.info("Request to " + appStoreUrl + " arrived at: " + currentUrl);
      Assert.assertEquals("default appstore URL was not as expected", EXPECTED_DEFAULT_APPSTORE_URL, currentUrl);
   }

   @Test
   public void overrideSyspropAppstoreUrl() {
      final String overrideTo = "https://www.google.com/";
      System.setProperty(NAME_SYSPROP_ENVVAR_APPSTORE_URL, overrideTo);
      final Response response = ClientBuilder.newClient().target(
              appStoreUrl.toExternalForm()).request().get();
      final String currentUrl = response.getLocation().toString();
      log.info("Request to " + appStoreUrl + " arrived at: " + currentUrl);
      Assert.assertEquals("appstore URL was not as expected when overriding via sysprop", overrideTo, currentUrl);
   }

   @Test
   public void overrideEnvVarAppstoreUrl() {
      final String overrideTo = "https://www.google.com/";
      EnvironmentVariableController.setEnv(NAME_SYSPROP_ENVVAR_APPSTORE_URL, overrideTo);
      final Response response = ClientBuilder.newClient().target(
              appStoreUrl.toExternalForm()).request().get();
      final String currentUrl = response.getLocation().toString();
      log.info("Request to " + appStoreUrl + " arrived at: " + currentUrl);
      Assert.assertEquals("appstore URL was not as expected when overriding via env var", overrideTo, currentUrl);
   }

   @Test
   public void overrideSyspropPrecedenceOverEnvVarAppstoreUrl() {
      final String overrideTo = "https://www.google.com/";
      // Set both sysprop and env var, expect sysprop to be honored
      System.setProperty(NAME_SYSPROP_ENVVAR_APPSTORE_URL, overrideTo);
      EnvironmentVariableController.setEnv(NAME_SYSPROP_ENVVAR_APPSTORE_URL, "sysprop should have precedence");
      final Response response = ClientBuilder.newClient().target(
              appStoreUrl.toExternalForm()).request().get();
      final String currentUrl = response.getLocation().toString();
      log.info("Request to " + appStoreUrl + " arrived at: " + currentUrl);
      Assert.assertEquals("appstore URL was not as expected from sysprop value when overriding via env var and sysprop", overrideTo, currentUrl);
   }

}
