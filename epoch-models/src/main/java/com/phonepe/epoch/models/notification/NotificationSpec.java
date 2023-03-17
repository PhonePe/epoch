package com.phonepe.epoch.models.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "MAIL", value = MailNotificationSpec.class),
        @JsonSubTypes.Type(name = "BLACKHOLE", value = BlackholeNotificationSpec.class),
})
public abstract class NotificationSpec {
    private final NotificationReceiverType type;

    protected NotificationSpec(NotificationReceiverType type) {
        this.type = type;
    }

    public abstract <T> T accept(final NotificationSpecVisitor<T> visitor);
}
