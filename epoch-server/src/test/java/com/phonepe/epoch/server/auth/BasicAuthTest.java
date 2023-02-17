package com.phonepe.epoch.server.auth;

import com.codahale.metrics.SharedMetricRegistries;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.auth.config.BasicAuthConfig;
import com.phonepe.epoch.server.auth.config.UserAuthInfo;
import com.phonepe.epoch.server.auth.core.EpochAuthenticator;
import com.phonepe.epoch.server.auth.core.EpochAuthorizer;
import com.phonepe.epoch.server.auth.models.EpochUser;
import com.phonepe.epoch.server.auth.models.EpochUserRole;
import com.phonepe.epoch.server.leadership.TestResource;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.CachingAuthorizer;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.jersey.jackson.JacksonFeature;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.apache.hc.core5.http.HttpStatus;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class BasicAuthTest extends TestBase {
    private static final BasicAuthConfig AUTH_CONFIG
            = new BasicAuthConfig(true,
                                  List.of(new UserAuthInfo("test_ro", "pwd", EpochUserRole.READ_ONLY),
                                          new UserAuthInfo("test_rw", "pwd", EpochUserRole.READ_WRITE)),
                                  BasicAuthConfig.DEFAULT_CACHE_POLICY);
    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addResource(new TestResource())
            .addProvider(new AuthDynamicFeature(new BasicCredentialAuthFilter.Builder<EpochUser>()
                                                        .setAuthenticator(new CachingAuthenticator<>(
                                                                SharedMetricRegistries.getOrCreate("test"),
                                                                new EpochAuthenticator(AUTH_CONFIG),
                                                                CaffeineSpec.parse(BasicAuthConfig.DEFAULT_CACHE_POLICY)))
                                                        .setAuthorizer(new CachingAuthorizer<>(
                                                                SharedMetricRegistries.getOrCreate("test"),
                                                                new EpochAuthorizer(),
                                                                CaffeineSpec.parse(BasicAuthConfig.DEFAULT_CACHE_POLICY)))
                                                        .setPrefix("Basic")
                                                        .buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(EpochUser.class))
            .build();

    @Test
    void testROAuthFail() {
        try(val r = EXT.target("/").request().get()) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, r.getStatus());
        }
    }

    @Test
    void testROAuthSuccess() {
        val client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basicBuilder()
                                  .nonPreemptive()
                                  .credentials("test_ro", "pwd")
                                  .build())
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().get()) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
        }
    }

    @Test
    void testROAuthWrongPwd() {
        val client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basicBuilder()
                                  .nonPreemptive()
                                  .credentials("test_ro", "wrong-pwd")
                                  .build())
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().get()) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, r.getStatus());
        }
    }

    @Test
    void testROAuthWrongPerm() {
        val client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basicBuilder()
                                  .nonPreemptive()
                                  .credentials("test_ro", "pwd")
                                  .build())
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().post(Entity.text("SS"))) {
            assertEquals(HttpStatus.SC_FORBIDDEN, r.getStatus());
        }
    }

    @Test
    void testROAuthPermSuccess() {
        val client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basicBuilder()
                                  .nonPreemptive()
                                  .credentials("test_rw", "pwd")
                                  .build())
                .register(new JacksonFeature(MAPPER))
                .build();
        try (val r = client.target(EXT.target("/").getUri()).request().post(Entity.text("SS"))) {
            assertEquals(HttpStatus.SC_OK, r.getStatus());
        }
    }
}