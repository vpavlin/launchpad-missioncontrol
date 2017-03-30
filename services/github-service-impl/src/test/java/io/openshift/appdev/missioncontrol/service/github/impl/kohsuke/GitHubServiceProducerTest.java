package io.openshift.appdev.missioncontrol.service.github.impl.kohsuke;

import io.openshift.appdev.missioncontrol.base.identity.IdentityFactory;
import org.junit.Assert;
import org.junit.Test;
import io.openshift.appdev.missioncontrol.service.github.api.GitHubService;

/**
 * Tests for the {@link GitHubServiceFactoryImpl}
 *
 * @author <a href="mailto:alr@redhat.com">Andrew Lee Rubinger</a>
 */
public class GitHubServiceProducerTest {

    @Test(expected = IllegalArgumentException.class)
    public void identityCannotBeNull() {
        new GitHubServiceFactoryImpl().create(null);
    }

    @Test
    public void createsInstance() {
        // when
        final GitHubService service = new GitHubServiceFactoryImpl().create(IdentityFactory.createFromUserPassword("test", "test"));
        // then
        Assert.assertNotNull("instance was not created", service);
    }
}
