package com.phonepe.epoch.server.auth;

import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.auth.core.EpochAuthorizer;
import com.phonepe.epoch.server.auth.filters.DummyAuthFilter;
import com.phonepe.epoch.server.auth.models.EpochUser;
import com.phonepe.epoch.server.leadership.TestResource;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.apache.hc.core5.http.HttpStatus;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class DummyAuthTest extends TestBase {
    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addResource(new TestResource())
            .addProvider(new AuthDynamicFeature(new DummyAuthFilter.Builder()
                                                        .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                                                        .setAuthorizer(new EpochAuthorizer())
                                                        .buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(EpochUser.class))
            .build();

    @Test
    void testROAuthSuccess() {
        try(val r = EXT.target("/").request().get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
        }
    }

    @Test
    void testROAuthPermSuccess() {
        try(val r = EXT.target("/").request().post(Entity.text("SS"))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
        }
    }

}