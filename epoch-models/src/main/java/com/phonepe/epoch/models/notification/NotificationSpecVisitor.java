package com.phonepe.epoch.models.notification;

/**
 *
 */
public interface NotificationSpecVisitor<T> {
    T visit(MailNotificationSpec mailSpec);

    T visit(BlackholeNotificationSpec blackhole);
}
