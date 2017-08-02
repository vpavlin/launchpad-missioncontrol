package io.openshift.appdev.missioncontrol.service.openshift.impl.fabric8.openshift.client;

import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import io.openshift.appdev.missioncontrol.base.identity.Identity;
import io.openshift.appdev.missioncontrol.service.openshift.api.OpenShiftService;
import io.openshift.appdev.missioncontrol.service.openshift.api.OpenShiftServiceFactory;
import io.openshift.appdev.missioncontrol.service.openshift.api.OpenShiftSettings;

/**
 * {@link OpenShiftServiceFactory} implementation
 *
 * @author <a href="mailto:xcoulon@redhat.com">Xavier Coulon</a>
 */
@ApplicationScoped
public class Fabric8OpenShiftServiceFactory implements OpenShiftServiceFactory {

    private Logger log = Logger.getLogger(Fabric8OpenShiftServiceFactory.class.getName());

    /**
     * Creates a new {@link OpenShiftService} with the specified credentials
     *
     * @param identity the credentials to use for this {@link OpenShiftService}
     * @return the created {@link OpenShiftService}
     * @throws IllegalArgumentException If the {@code identity} is not specified
     */
    @Override
    public Fabric8OpenShiftServiceImpl create(Identity identity) {
        final String apiUrl = OpenShiftSettings.getOpenShiftApiUrl();
        final String consoleUrl = OpenShiftSettings.getOpenShiftConsoleUrl();
        return create(apiUrl, consoleUrl, identity);
    }

    /**
     * Creates a new {@link OpenShiftService} with the specified, required urls and oauthToken
     *
     * @param identity the credentials to use for this {@link OpenShiftService
     * @return the created {@link OpenShiftService}
     * @throws IllegalArgumentException If the {@code identity} is not specified
     */
    @Override
    public Fabric8OpenShiftServiceImpl create(String apiUrl, String consoleUrl, Identity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("identity is required");
        }

        // Precondition checks
        if (apiUrl == null) {
            throw new IllegalArgumentException("openshiftUrl is required");
        }
        if (consoleUrl == null) {
            throw new IllegalArgumentException("openshiftConsoleUrl is required");
        }

        // Create and return
        log.finest(() -> "Created backing OpenShift client for " + apiUrl);
        return new Fabric8OpenShiftServiceImpl(apiUrl, consoleUrl, identity);
    }
}
