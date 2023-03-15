package com.phonepe.epoch.server.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.epoch.models.notification.NotificationReceiverType;
import lombok.Data;

/**
 *
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "MAIL", value = MailNotificationConfig.class)
})
public abstract class NotificationConfig {
    private final NotificationReceiverType type;

    protected NotificationConfig(NotificationReceiverType type) {
        this.type = type;
    }

    public abstract <T> T accept(final NotificationConfigVisitor<T> visitor);
}
