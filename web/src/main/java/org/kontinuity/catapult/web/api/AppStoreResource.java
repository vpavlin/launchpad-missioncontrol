package org.kontinuity.catapult.web.api;

import org.kontinuity.catapult.base.EnvironmentSupport;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Endpoint to request the appstore
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
@Path(AppStoreResource.PATH_APPSTORE)
@ApplicationScoped
public class AppStoreResource {

   /*
    Paths
   */
   public static final String PATH_APPSTORE = "/appstore";
   public static final String PATH_URL = "/applications";

   private static final String MIME_JSON = "application/json";

   private static final String DEFAULT_APPSTORE_URL = "https://raw.githubusercontent.com/redhat-kontinuity/appstore/master/appstore.json";

   private static final String NAME_SYSPROP_CATAPULT_APP_STORE_URL = "CATAPULT_APPSTORE_URL";

   /**
    * Provides a list of applications in the appstore in JSON format
    *
    * @return
    */
   @GET
   @Path(PATH_URL)
   @Produces(MIME_JSON)
   public Response applications() {

      /*
        Implementation note; at the moment we're an indirection to the
        backing appstore, so we just send an HTTP 303 redirect to the client
        to get it on their own.  Future implementations may instead choose to serve
        the appstore content directly by caching/filtering.  But this API point should
        remain fixed for clients to request the appstore.
       */

      // Default the app store URL
      String appStoreUrlString = DEFAULT_APPSTORE_URL;

      // Override from env var or sys prop if exists
      final String appStoreUrlFromEnvVarOrSysProp;
      appStoreUrlFromEnvVarOrSysProp = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(NAME_SYSPROP_CATAPULT_APP_STORE_URL);
      if (appStoreUrlFromEnvVarOrSysProp != null && !appStoreUrlFromEnvVarOrSysProp.isEmpty()) {
         appStoreUrlString = appStoreUrlFromEnvVarOrSysProp;
      }

      // Create URI Representation
      final URI appStoreUri;
      try {
         appStoreUri = new URI(appStoreUrlString);
      } catch (final URISyntaxException urise) {
         throw new RuntimeException("Configured URL for appstore, " +
                 appStoreUrlString + " is invalid", urise);
      }

      // Send redirect
      return Response.temporaryRedirect(appStoreUri).build();
   }

}
