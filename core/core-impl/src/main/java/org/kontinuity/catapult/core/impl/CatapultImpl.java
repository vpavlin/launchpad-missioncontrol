package org.kontinuity.catapult.core.impl;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;

import org.kontinuity.catapult.core.api.Boom;
import org.kontinuity.catapult.core.api.Catapult;
import org.kontinuity.catapult.core.api.CreateProjectile;
import org.kontinuity.catapult.core.api.ForkProjectile;
import org.kontinuity.catapult.core.api.Projectile;
import org.kontinuity.catapult.core.api.StatusMessage;
import org.kontinuity.catapult.core.api.StatusMessageEvent;
import org.kontinuity.catapult.service.github.api.DuplicateWebhookException;
import org.kontinuity.catapult.service.github.api.GitHubRepository;
import org.kontinuity.catapult.service.github.api.GitHubService;
import org.kontinuity.catapult.service.github.api.GitHubServiceFactory;
import org.kontinuity.catapult.service.github.api.GitHubWebhook;
import org.kontinuity.catapult.service.github.api.GitHubWebhookEvent;
import org.kontinuity.catapult.service.github.spi.GitHubServiceSpi;
import org.kontinuity.catapult.service.openshift.api.OpenShiftProject;
import org.kontinuity.catapult.service.openshift.api.OpenShiftService;
import org.kontinuity.catapult.service.openshift.api.OpenShiftServiceFactory;

import static java.util.Collections.singletonMap;

/**
 * Implementation of the {@link Catapult} interface.
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
public class CatapultImpl implements Catapult {

    private static final Logger log = Logger.getLogger(CatapultImpl.class.getName());

    @Inject
    private OpenShiftServiceFactory openShiftServiceFactory;

    @Inject
    private GitHubServiceFactory gitHubServiceFactory;

    @Inject
    Event<StatusMessageEvent> event;

    /**
     * {@inheritDoc}
     */
    @Override
    public Boom fling(final ForkProjectile projectile) throws IllegalArgumentException {

        final GitHubService gitHubService = getGitHubService(projectile);
        GitHubRepository gitHubRepository;
        // Get properties
        final String sourceRepoName = projectile.getSourceGitHubRepo();
        gitHubRepository = gitHubService.fork(sourceRepoName);

        //TODO
        // https://github.com/redhat-kontinuity/catapult/issues/18
        // Create a new OpenShift project for the user
        final String projectName = projectile.getOpenShiftProjectName();

        /*
          TODO Figure how to best handle possible DuplicateProjectException, but has to be handled to the user at some intelligent level
         */
        OpenShiftService openShiftService = openShiftServiceFactory.create(projectile.getOpenShiftIdentity());
        final OpenShiftProject createdProject = openShiftService.createProject(projectName);

        /*
         * Construct the full URI for the pipeline template file,
         * relative to the repository root
         */
        final URI pipelineTemplateUri = UriBuilder.fromUri("https://raw.githubusercontent.com/")
                .path(projectile.getSourceGitHubRepo())
                .path(projectile.getGitRef())
                .path(projectile.getPipelineTemplatePath()).build();

        // Configure the OpenShift project
        openShiftService.configureProject(createdProject,
                                          gitHubRepository.getGitCloneUri(),
                                          projectile.getGitRef(),
                                          pipelineTemplateUri);

        GitHubWebhook webhook = getGitHubWebhook(gitHubService, openShiftService, gitHubRepository, createdProject);

        // Return information needed to continue flow to the user
        return new BoomImpl(gitHubRepository, createdProject, webhook);
    }

    @Override
    public Boom fling(CreateProjectile projectile) throws IllegalArgumentException {
        final GitHubService gitHubService = getGitHubService(projectile);
        String projectName = projectile.getOpenShiftProjectName();
        File path = projectile.getProjectLocation().toFile();
        String gitHubRepositoryDescription = projectile.getGitHubRepositoryDescription();
        GitHubRepository gitHubRepository = gitHubService.createRepository(projectName, gitHubRepositoryDescription);
        event.fire(new StatusMessageEvent(projectile.getId(), StatusMessage.GITHUB_CREATE, singletonMap("location", gitHubRepository.getHomepage())));
        gitHubService.push(gitHubRepository, path);
        fireEvent(projectile, StatusMessage.GITHUB_PUSHED);

        OpenShiftService openShiftService = openShiftServiceFactory.create(projectile.getOpenShiftIdentity());
        OpenShiftProject createdProject = openShiftService.createProject(projectName);
        event.fire(new StatusMessageEvent(projectile.getId(), StatusMessage.OPENSHIFT_CREATE, singletonMap("location", createdProject.getConsoleOverviewUrl())));
        openShiftService.configureProject(createdProject, gitHubRepository.getGitCloneUri());

        GitHubWebhook webhook = getGitHubWebhook(gitHubService, openShiftService, gitHubRepository, createdProject);
        fireEvent(projectile, StatusMessage.GITHUB_WEBHOOK);
        return new BoomImpl(gitHubRepository, createdProject, webhook);
    }

    private void fireEvent(Projectile projectile, StatusMessage status) {
        event.fire(new StatusMessageEvent(projectile.getId(), status));
    }


    private GitHubWebhook getGitHubWebhook(GitHubService gitHubService, OpenShiftService openShiftService,
                                           GitHubRepository gitHubRepository, OpenShiftProject createdProject) {
        GitHubWebhook webhook;
        final URL webhookUrl = openShiftService.getWebhookUrl(createdProject);
        if (webhookUrl != null) {
            try {
                webhook = gitHubService.createWebhook(gitHubRepository, webhookUrl, GitHubWebhookEvent.PUSH);
            } catch (final DuplicateWebhookException dpe) {
                // Swallow, it's OK, we've already forked this repo
                log.log(Level.INFO, dpe.getMessage());
                webhook = ((GitHubServiceSpi) gitHubService)
                        .getWebhook(gitHubRepository, webhookUrl);
            }
        } else {
            webhook = null;
        }
        return webhook;
    }

    private GitHubService getGitHubService(Projectile projectile) {
        if (projectile == null) {
            throw new IllegalArgumentException("projectile must be specified");
        }
        return gitHubServiceFactory.create(projectile.getGitHubIdentity());
    }
}
