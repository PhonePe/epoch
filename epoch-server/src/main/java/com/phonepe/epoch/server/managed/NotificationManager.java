package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.notify.NotificationSender;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;

/**
 *
 */
@Order(80)
@Slf4j
public class NotificationManager implements Managed {

    private final NotificationSender notificationSender;
    private final EpochEventBus eventBus;

    @Inject
    public NotificationManager(NotificationSender notificationSender, EpochEventBus eventBus) {
        this.notificationSender = notificationSender;
        this.eventBus = eventBus;
    }

    @Override
    public void start() throws Exception {
        eventBus.onNewEvent().connect("NOTIFICATION_HANDLER", notificationSender);
        log.info("Notification sender connected");
    }

    @Override
    public void stop() throws Exception {
        eventBus.onNewEvent().disconnect("NOTIFICATION_HANDLER");
        log.info("Notification sender disconnected");

    }
}
