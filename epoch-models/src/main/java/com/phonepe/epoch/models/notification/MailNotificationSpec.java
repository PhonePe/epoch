package com.phonepe.epoch.models.notification;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class MailNotificationSpec extends NotificationSpec {

    @NotEmpty
    List<@Email String> emails;

    public MailNotificationSpec(List<String> emails) {
        super(NotificationReceiverType.MAIL);
        this.emails = emails;
    }

    @Override
    public <T> T accept(NotificationSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
