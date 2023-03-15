package com.phonepe.epoch.models.notification;

/**
 *
 */
public class BlackholeNotificationSpec extends NotificationSpec {
    public static final BlackholeNotificationSpec DEFAULT = new BlackholeNotificationSpec();

    public BlackholeNotificationSpec() {
        super(NotificationReceiverType.BLACKHOLE);
    }

    @Override
    public <T> T accept(NotificationSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
