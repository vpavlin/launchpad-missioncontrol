package org.kontinuity.catapult.test;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import javax.ws.rs.core.UriBuilder;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kontinuity.catapult.core.api.StatusMessage;
import org.kontinuity.catapult.core.api.StatusMessageEvent;
import org.kontinuity.catapult.web.api.websocket.CatapultStatusEndpoint;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validation of the {@link CatapultStatusEndpoint}
 */
@RunWith(Arquillian.class)
public class CatapultStatusEndpointIT {

    private static final String EXTRA_DATA_KEY = "GitHub project";

    @Inject
    Event<StatusMessageEvent> testEvent;

    @Inject
    StatusTestClientEndpoint endpoint;

    @ArquillianResource
    private URI deploymentUrl;

    @Deployment
    public static WebArchive getDeployment() {
        return Deployments.getMavenBuiltWar().addClass(StatusTestClientEndpoint.class);
    }

    /**
     * Ensures that CDI event is relayed over the webSocket status endpoint.
     *
     * @throws Exception when the test has failed
     */
    @Test
    public void webSocketsStatusTest() throws Exception {
        //given
        UUID uuid = UUID.randomUUID();
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        URI uri = UriBuilder.fromUri(deploymentUrl).scheme("ws").path("status").path(uuid.toString()).build();
        container.connectToServer(endpoint, uri);

        //when
        Thread.sleep(200);
        testEvent.fire(new StatusMessageEvent(uuid, StatusMessage.GITHUB_CREATE,
                                              singletonMap(EXTRA_DATA_KEY, "http://github.com/dummy-project-location")));
        endpoint.getLatch().await(1, TimeUnit.SECONDS);

        //then
        assertNotNull("a status message should have been send", endpoint.getMessage());
        assertTrue(endpoint.getMessage().contains(EXTRA_DATA_KEY));
    }

}
