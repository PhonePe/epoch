package com.phonepe.epoch.server.config;

import com.phonepe.epoch.models.notification.NotificationReceiverType;

/**
 *
 */
public class BlackholeNotificationConfig extends NotificationConfig {
    public static final BlackholeNotificationConfig INSTANCE = new BlackholeNotificationConfig();

    private BlackholeNotificationConfig() {
        super(NotificationReceiverType.BLACKHOLE);
    }

    @Override
    public <T> T accept(NotificationConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
