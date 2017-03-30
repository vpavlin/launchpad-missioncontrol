package org.kontinuity.catapult.web.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.kontinuity.catapult.base.EnvironmentSupport;
import org.kontinuity.catapult.base.identity.Identity;
import org.kontinuity.catapult.base.identity.IdentityFactory;
import org.kontinuity.catapult.core.api.Catapult;
import org.kontinuity.catapult.core.api.CreateProjectile;
import org.kontinuity.catapult.core.api.ForkProjectile;
import org.kontinuity.catapult.core.api.ProjectileBuilder;
import org.kontinuity.catapult.service.keycloak.api.KeycloakService;

/**
 * Endpoint exposing the {@link org.kontinuity.catapult.core.api.Catapult} over HTTP
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
@Path(CatapultResource.PATH_CATAPULT)
@ApplicationScoped
public class CatapultResource {

    /**
     * Paths
     **/
    static final String PATH_CATAPULT = "/catapult";

    private static final String PATH_FLING = "/fling";

    private static final String PATH_UPLOAD = "/upload";

    private static final String PATH_STATUS = "/status";

    /*
     Catapult Query Parameters
     */
    private static final String QUERY_PARAM_SOURCE_REPO = "sourceRepo";

    private static final String QUERY_PARAM_GIT_REF = "gitRef";

    private static final String QUERY_PARAM_PIPELINE_TEMPLATE_PATH = "pipelineTemplatePath";

    private static final String CATAPULT_OPENSHIFT_USERNAME = "CATAPULT_OPENSHIFT_USERNAME";

    private static final String CATAPULT_OPENSHIFT_PASSWORD = "CATAPULT_OPENSHIFT_PASSWORD";

    private static final String CATAPULT_OPENSHIFT_TOKEN = "CATAPULT_OPENSHIFT_TOKEN";

    private static final String CATAPULT_GITHUB_TOKEN = "CATAPULT_GITHUB_TOKEN";

    private static Logger log = Logger.getLogger(CatapultResource.class.getName());

    @Inject
    private Catapult catapult;

    @Inject
    private KeycloakService keycloakService;

    @Resource
    ManagedExecutorService executorService;

    @GET
    @Path(PATH_FLING)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject fling(
            @Context final HttpServletRequest request,
            @NotNull @QueryParam(QUERY_PARAM_SOURCE_REPO) final String sourceGitHubRepo,
            @NotNull @QueryParam(QUERY_PARAM_GIT_REF) final String gitRef,
            @NotNull @QueryParam(QUERY_PARAM_PIPELINE_TEMPLATE_PATH) final String pipelineTemplatePath,
            @NotNull @HeaderParam(HttpHeaders.AUTHORIZATION) final String authorization) {

        Identity githubIdentity;
        Identity openShiftIdentity;
        if (useDefaultIdentities()) {
            githubIdentity = getDefaultGithubIdentity();
            openShiftIdentity = getDefaultOpenShiftIdentity();
        } else {
            githubIdentity = keycloakService.getGitHubIdentity(authorization);
            openShiftIdentity = keycloakService.getOpenShiftIdentity(authorization);
        }

        ForkProjectile projectile = ProjectileBuilder.newInstance()
                .gitHubIdentity(githubIdentity)
                .openShiftIdentity(openShiftIdentity)
                .forkType()
                .sourceGitHubRepo(sourceGitHubRepo)
                .gitRef(gitRef)
                .pipelineTemplatePath(pipelineTemplatePath)
                .build();
        // Fling it
        executorService.submit(() -> catapult.fling(projectile));
        return Json.createObjectBuilder()
                .add("uuid", projectile.getId().toString())
                .add("uuid_link", PATH_CATAPULT + PATH_STATUS + "/" + projectile.getId().toString())
                .build();
    }

    @POST
    @Path(PATH_UPLOAD)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject upload(
            @HeaderParam(HttpHeaders.AUTHORIZATION) final String authorization,
            @MultipartForm UploadForm form) {
        Identity githubIdentity;
        Identity openShiftIdentity;
        if (useDefaultIdentities()) {
            githubIdentity = getDefaultGithubIdentity();
            openShiftIdentity = getDefaultOpenShiftIdentity();
        } else {
            githubIdentity = keycloakService.getGitHubIdentity(authorization);
            openShiftIdentity = keycloakService.getOpenShiftIdentity(authorization);
        }
        try {
            final java.nio.file.Path tempDir = Files.createTempDirectory("tmpUpload");
            try (InputStream inputStream = form.getFile()) {
                FileUploadHelper.unzip(inputStream, tempDir);
                try (DirectoryStream<java.nio.file.Path> projects = Files.newDirectoryStream(tempDir)) {
                    java.nio.file.Path project = projects.iterator().next();
                    CreateProjectile projectile = ProjectileBuilder.newInstance()
                            .gitHubIdentity(githubIdentity)
                            .openShiftIdentity(openShiftIdentity)
                            .createType()
                            .gitHubRepositoryDescription(form.getGitHubRepositoryDescription())
                            .projectLocation(project)
                            .build();
                    // Fling it
                    CompletableFuture.supplyAsync(() -> catapult.fling(projectile), executorService)
                            .whenComplete((boom, ex) -> FileUploadHelper.deleteDirectory(tempDir));
                    return Json.createObjectBuilder()
                            .add("uuid", projectile.getId().toString())
                            .add("uuid_link", PATH_CATAPULT + PATH_STATUS + "/" + projectile.getId().toString())
                            .build();
                }
            }
        } catch (final IOException e) {
            throw new WebApplicationException("could not unpack zip file into temp folder", e);
        }
    }

    private Identity getDefaultOpenShiftIdentity() {
        // Read from the ENV variables
        String token = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(CATAPULT_OPENSHIFT_TOKEN);
        if (token != null) {
            return IdentityFactory.createFromToken(token);
        } else {
            String user = EnvironmentSupport.INSTANCE.getRequiredEnvVarOrSysProp(CATAPULT_OPENSHIFT_USERNAME);
            String password = EnvironmentSupport.INSTANCE.getRequiredEnvVarOrSysProp(CATAPULT_OPENSHIFT_PASSWORD);
            return IdentityFactory.createFromUserPassword(user, password);
        }
    }

    private Identity getDefaultGithubIdentity() {
        // Try using the provided Github token
        String token = EnvironmentSupport.INSTANCE.getRequiredEnvVarOrSysProp(CATAPULT_GITHUB_TOKEN);
        return IdentityFactory.createFromToken(token);
    }

    private boolean useDefaultIdentities() {
        String user = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(CATAPULT_OPENSHIFT_USERNAME);
        String password = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(CATAPULT_OPENSHIFT_PASSWORD);
        String token = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(CATAPULT_OPENSHIFT_TOKEN);

        return ((user != null && password != null) || token != null);
    }
}