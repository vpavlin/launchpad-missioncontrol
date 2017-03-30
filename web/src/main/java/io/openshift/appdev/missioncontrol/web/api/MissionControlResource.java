package io.openshift.appdev.missioncontrol.web.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.openshift.appdev.missioncontrol.base.EnvironmentSupport;
import io.openshift.appdev.missioncontrol.base.identity.Identity;
import io.openshift.appdev.missioncontrol.base.identity.IdentityFactory;
import io.openshift.appdev.missioncontrol.core.api.*;
import io.openshift.appdev.missioncontrol.core.api.MissionControl;
import io.openshift.appdev.missioncontrol.service.keycloak.api.KeycloakService;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

/**
 * Endpoint exposing the {@link MissionControl} over HTTP
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
@Path(MissionControlResource.PATH_MISSIONCONTROL)
@ApplicationScoped
public class MissionControlResource {

    /**
     * Paths
     **/
    static final String PATH_MISSIONCONTROL = "/missioncontrol";

    private static final String PATH_LAUNCH = "/launch";

    private static final String PATH_UPLOAD = "/upload";

    /*
     MissionControl Query Parameters
     */
    private static final String QUERY_PARAM_SOURCE_REPO = "sourceRepo";

    private static final String QUERY_PARAM_GIT_REF = "gitRef";

    private static final String QUERY_PARAM_PIPELINE_TEMPLATE_PATH = "pipelineTemplatePath";

    private static final String LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_USERNAME = "LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_USERNAME";

    private static final String LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_PASSWORD = "LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_PASSWORD";

    private static final String LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_TOKEN = "LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_TOKEN";

    private static final String LAUNCHPAD_MISSIONCONTROL_GITHUB_TOKEN = "LAUNCHPAD_MISSIONCONTROL_GITHUB_TOKEN";

    private static Logger log = Logger.getLogger(MissionControlResource.class.getName());

    @Inject
    private MissionControl missionControl;

    @Inject
    private KeycloakService keycloakService;

    @GET
    @Path(PATH_LAUNCH)
    public Response fling(
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
        Boom boom = missionControl.launch(projectile);
        return processBoom(boom);
    }

    @POST
    @Path(PATH_UPLOAD)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
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
        java.nio.file.Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("tmpUpload");
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
                    Boom boom = missionControl.launch(projectile);
                    return processBoom(boom);
                }
            }
        } catch (final IOException e) {
            throw new WebApplicationException("could not unpack zip file into temp folder", e);
        } finally {
            try {
                FileUploadHelper.deleteDirectory(tempDir);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Could not delete " + tempDir, e);
            }
        }
    }


    private Response processBoom(Boom boom) {
        // Redirect to the console overview page
        final URI consoleOverviewUri;
        try {
            consoleOverviewUri = boom.getCreatedProject().getConsoleOverviewUrl().toURI();
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Redirect issued to: " + consoleOverviewUri.toString());
            }
        } catch (final URISyntaxException urise) {
            throw new WebApplicationException("couldn't get console location do you have the environment variable set?", urise);
        }
        return Response.temporaryRedirect(consoleOverviewUri).build();
    }


    private Identity getDefaultOpenShiftIdentity() {
        // Read from the ENV variables
        String token = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_TOKEN);
        if (token != null) {
            return IdentityFactory.createFromToken(token);
        } else {
            String user = EnvironmentSupport.INSTANCE.getRequiredEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_USERNAME);
            String password = EnvironmentSupport.INSTANCE.getRequiredEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_PASSWORD);
            return IdentityFactory.createFromUserPassword(user, password);
        }
    }

    private Identity getDefaultGithubIdentity() {
        // Try using the provided Github token
        String token = EnvironmentSupport.INSTANCE.getRequiredEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_GITHUB_TOKEN);
        return IdentityFactory.createFromToken(token);
    }

    private boolean useDefaultIdentities() {
        String user = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_USERNAME);
        String password = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_PASSWORD);
        String token = EnvironmentSupport.INSTANCE.getEnvVarOrSysProp(LAUNCHPAD_MISSIONCONTROL_OPENSHIFT_TOKEN);

        return ((user != null && password != null) || token != null);
    }

}