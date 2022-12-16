package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.server.event.EpochEventBus;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Order(60)
@Singleton
public class EpochEventLogger implements Managed {
    private final EpochEventBus eventBus;

    @Inject
    public EpochEventLogger(EpochEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void start() throws Exception {
        eventBus.onNewEvent().connect("event-logger", e -> log.info("EPOCH_EVENT: {}", e));
    }

    @Override
    public void stop() throws Exception {
        eventBus.onNewEvent().disconnect("event-logger");
    }
}
