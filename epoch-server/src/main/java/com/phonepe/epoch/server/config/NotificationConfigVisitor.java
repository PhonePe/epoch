package com.phonepe.epoch.server.config;

/**
 *
 */
public interface NotificationConfigVisitor<T> {
    T visit(MailNotificationConfig mailConfig);

    T visit(BlackholeNotificationConfig blackholeConfig);
}
