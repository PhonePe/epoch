package com.phonepe.epoch.server.leadership;

import com.phonepe.epoch.server.managed.LeadershipManager;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class LeaderRoutingFilterTest {
    private static final LeadershipManager LM;
    private static final LeadershipManager LM_LEADER;
    private static final ResourceExtension EXT;
    private static final ResourceExtension EXT_LEADER;

    static {
        LM = mock(LeadershipManager.class);
        LM_LEADER = mock(LeadershipManager.class);
        EXT = ResourceExtension.builder()
                .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                .addResource(new TestResource())
                .addProvider(new LeaderRoutingFilter(LM))
                .build();
        EXT_LEADER = ResourceExtension.builder()
                .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                .addResource(new TestResource())
                .addProvider(new LeaderRoutingFilter(LM_LEADER))
                .build();
    }

    @BeforeEach
    void setup() {
        //Nothing
    }

    @AfterEach
    void teardown() {
        reset(LM);
        reset(LM_LEADER);
    }

    @Test
    void testSuccessLocal() {
        when(LM.isLeader()).thenReturn(true);
        val res = EXT.target("/").request().get();
        assertEquals(HttpStatus.OK_200, res.getStatus());
    }
    @Test
    void testFailNoLeader() {
        when(LM.isLeader()).thenReturn(false);
        when(LM.leader()).thenReturn(Optional.empty());

        val res = EXT.target("/").request().get();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR_500, res.getStatus());
    }
    @Test
    void testSuccessRemoteGet() {
        when(LM.isLeader()).thenReturn(false);
        when(LM_LEADER.isLeader()).thenReturn(true);
        when(LM.leader()).thenReturn(Optional.of(EXT_LEADER.target("/").getUri().toString()));
        System.out.println(LM.leader());

        val res = EXT.target("/").request().get();
        assertEquals(HttpStatus.OK_200, res.getStatus());
    }
    @Test
    void testSuccessRemotePost() {
        when(LM.isLeader()).thenReturn(false);
        when(LM_LEADER.isLeader()).thenReturn(true);
        when(LM.leader()).thenReturn(Optional.of(EXT_LEADER.target("/").getUri().toString()));
        System.out.println(LM.leader());

        try(val res = EXT.target("/").request().post(Entity.text("Santanu"))) {
            assertEquals(HttpStatus.OK_200, res.getStatus());
            assertEquals("{\"name\":\"Santanu\"}", res.readEntity(String.class));
        }
    }

    @Test
    void testSuccessRemotePut() {
        when(LM.isLeader()).thenReturn(false);
        when(LM_LEADER.isLeader()).thenReturn(true);
        when(LM.leader()).thenReturn(Optional.of(EXT_LEADER.target("/").getUri().toString()));
        System.out.println(LM.leader());

        try(val res = EXT.target("/").request().post(Entity.text("Santanu"))) {
            assertEquals(HttpStatus.OK_200, res.getStatus());
            assertEquals("{\"name\":\"Santanu\"}", res.readEntity(String.class));
        }
    }
}