package com.phonepe.epoch.server;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.phonepe.epoch.server.config.AppConfig;
import com.phonepe.epoch.server.managed.Scheduler;
import com.phonepe.epoch.server.mocks.MockTaskExecutionEngine;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.ExecutorServiceBuilder;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.AdminEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.FixtureHelpers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class E2ETestBase {
    private static final HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    private static final JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    private static final LifecycleEnvironment lifecycleEnvironment = mock(LifecycleEnvironment.class);
    private static final Environment environment = mock(Environment.class);

    private static final Bootstrap<?> bootstrap = mock(Bootstrap.class);
    private static final AdminEnvironment adminEnvironment = mock(AdminEnvironment.class);
    private final OlympusIMBundle<AppConfig> olympusIMBundle = mock(OlympusIMBundle.class);
    protected Injector injector;
    protected ObjectMapper mapper;
    protected MockTaskExecutionEngine taskExecutionEngine = new MockTaskExecutionEngine();

    @Inject
    private Scheduler scheduler;

    public abstract Stream<Module> customModule();

    @BeforeAll
    public void setUp() throws Exception {
        when(jerseyEnvironment.getResourceConfig()).thenReturn(new DropwizardResourceConfig());
        when(lifecycleEnvironment.executorService(anyString()))
                .thenReturn(new ExecutorServiceBuilder(lifecycleEnvironment, "test"));
        ArgumentCaptor<Managed> valueCapture = ArgumentCaptor.forClass(Managed.class);
        doNothing().when(lifecycleEnvironment).manage(valueCapture.capture());
        when(environment.jersey()).thenReturn(jerseyEnvironment);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(environment.admin()).thenReturn(adminEnvironment);
        OlympusIMClient olympusIMClient = mock(OlympusIMClient.class);
        when(olympusIMClient.getSystemAuthHeader()).thenReturn("testHeader");
        when(olympusIMBundle.getOlympusIMClient()).thenReturn(olympusIMClient);

        when(bootstrap.getHealthCheckRegistry())
                .thenReturn(Mockito.mock(HealthCheckRegistry.class));

        injector = Guice.createInjector(
                Stream.concat(Stream.<Module>of(new TestModule(environment, mapper, taskExecutionEngine)),
                              customModule())
                        .toArray(Module[]::new));
        injector.injectMembers(this);

        valueCapture.getAllValues()
                .forEach(managed -> ignoredExec(managed::start));
        scheduler.start();
    }

    @BeforeEach
    public void setUpBeforeEach() {
        final MetricRegistry metricRegistry = injector.getInstance(MetricRegistry.class);
        metricRegistry.removeMatching((s, metric) -> true);
        taskExecutionEngine.reset();
    }

    public <T> T getInstance(Class<T> clazz) {
        return injector.getInstance(clazz);
    }

    @SneakyThrows
    public <T> T resourceToClass(String resourceFile, Class<T> tClass) {
        return mapper.readValue(FixtureHelpers.fixture(resourceFile), tClass);
    }

    @SneakyThrows
    public <T> T resourceToClass(String resourceFile, TypeReference<T> typeReference) {
        return mapper.readValue(FixtureHelpers.fixture(resourceFile), typeReference);
    }

    public void ignoredExec(ERunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.info("Error while running", e);
        }
    }

    public interface ERunnable {
        void run() throws Exception;
    }
}