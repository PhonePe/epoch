package com.phonepe.epoch.server.config;

import com.phonepe.epoch.models.notification.NotificationReceiverType;
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
public class MailNotificationConfig extends NotificationConfig {

    @NotEmpty
    String smtpServer;

    int port;

    boolean tls;

    String username;

    String password;

    List<@Email String> defaultEmails;

    String fromName;

    @NotEmpty
    String fromAddress;

    boolean disableForSuccessfulRuns;

    public MailNotificationConfig(
            String smtpServer,
            int port,
            boolean tls,
            String username,
            String password,
            List<@Email String> defaultEmails,
            String fromName,
            String fromAddress,
            boolean disableForSuccessfulRuns) {
        super(NotificationReceiverType.MAIL);
        this.smtpServer = smtpServer;
        this.port = port;
        this.tls = tls;
        this.username = username;
        this.password = password;
        this.defaultEmails = defaultEmails;
        this.fromName = fromName;
        this.fromAddress = fromAddress;
        this.disableForSuccessfulRuns = disableForSuccessfulRuns;
    }

    @Override
    public <T> T accept(NotificationConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
