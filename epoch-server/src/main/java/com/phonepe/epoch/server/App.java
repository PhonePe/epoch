package com.phonepe.epoch.server;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.google.common.base.Strings;
import com.google.inject.Stage;
import com.phonepe.epoch.server.auth.config.BasicAuthConfig;
import com.phonepe.epoch.server.auth.core.EpochAuthenticator;
import com.phonepe.epoch.server.auth.core.EpochAuthorizer;
import com.phonepe.epoch.server.auth.filters.DummyAuthFilter;
import com.phonepe.epoch.server.auth.models.EpochUser;
import com.phonepe.epoch.server.config.AppConfig;
import com.phonepe.epoch.server.ui.HandlebarsViewRenderer;
import com.phonepe.epoch.server.utils.IgnoreInJacocoGeneratedReport;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.Application;
import io.dropwizard.auth.*;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.server.AbstractServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.HealthCheckInstaller;
import ru.vyarus.guicey.gsp.ServerPagesBundle;

import java.util.Objects;

import static com.phonepe.epoch.server.utils.EpochUtils.configureMapper;

/**
 *
 */
@IgnoreInJacocoGeneratedReport(reason = "mostly used for wiring ... nothing to test")
public class App extends Application<AppConfig> {
    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                                               new EnvironmentVariableSubstitutor(true)));

        bootstrap.addBundle(
                GuiceBundle.builder()
                        .enableAutoConfig("com.phonepe.epoch.server.resources",
                                          "com.phonepe.epoch.server.managed",
                                          "com.phonepe.epoch.server.healthchecks",
                                          "com.phonepe.epoch.server.leadership",
                                          "com.phonepe.epoch.server.errorhandlers",
                                          "com.phonepe.epoch.server.error")
                        .modules(new EpochModule())
                        .installers(HealthCheckInstaller.class)
                        .bundles(ServerPagesBundle.builder()
                                         .addViewRenderers(new HandlebarsViewRenderer())
                                         .build())
                        .bundles(ServerPagesBundle.app("ui", "/assets/", "/")
                                         .mapViews("/ui")
                                         .requireRenderers("handlebars")
                                         .build())
                        .printDiagnosticInfo()
                        .build(Stage.PRODUCTION));
    }

    @Override
    public void run(AppConfig appConfig, Environment environment) throws Exception {
        FunctionMetricsManager.initialize("com.phonepe.epoch", environment.metrics());
        configureMapper(environment.getObjectMapper());
        ((AbstractServerFactory) appConfig.getServerFactory()).setJerseyRootPath("/apis/*");
        setupAuth(appConfig, environment, environment.jersey());
    }

    @SneakyThrows
    public static void main(String[] args) {
        val app = new App();
        app.run(args);
    }

    @SuppressWarnings("java:S1905") //Sonar bug does not identify base casting for var
    private void setupAuth(AppConfig appConfig, Environment environment, JerseyEnvironment jersey) {
        val basicAuthConfig = Objects.requireNonNullElse(appConfig.getUserAuth(), BasicAuthConfig.DEFAULT);
        var authFilter = (AuthFilter<?, EpochUser>)new DummyAuthFilter.Builder()
                .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                .setAuthorizer(new EpochAuthorizer())
                .buildAuthFilter();
        if(basicAuthConfig.isEnabled()) {
            val cacheConfig = Strings.isNullOrEmpty(basicAuthConfig.getCachingPolicy())
                              ? BasicAuthConfig.DEFAULT_CACHE_POLICY
                              : basicAuthConfig.getCachingPolicy();
            authFilter = new BasicCredentialAuthFilter.Builder<EpochUser>()
                    .setAuthenticator(new CachingAuthenticator<>(environment.metrics(),
                                                                 new EpochAuthenticator(basicAuthConfig),
                                                                 CaffeineSpec.parse(cacheConfig)))
                    .setAuthorizer(new CachingAuthorizer<>(environment.metrics(),
                                                           new EpochAuthorizer(),
                                                           CaffeineSpec.parse(cacheConfig)))
                    .setPrefix("Basic")
                    .buildAuthFilter();
        }
        jersey.register(new AuthDynamicFeature(authFilter));
        jersey.register(new AuthValueFactoryProvider.Binder<>(EpochUser.class));
        jersey.register(RolesAllowedDynamicFeature.class);
    }
}
