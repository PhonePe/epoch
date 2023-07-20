package com.phonepe.epoch.server.notify;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.epoch.server.config.MailNotificationConfig;
import com.phonepe.epoch.server.event.EpochEvent;
import com.phonepe.epoch.server.event.EpochEventVisitor;
import com.phonepe.epoch.server.utils.IgnoreInJacocoGeneratedReport;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Singleton
@SuppressWarnings("unchecked")
public class NotificationMailSender implements NotificationSender {

    private final EventMailDataConverter mailDataConverter;
    private final MailNotificationConfig mailConfig;
    private final Mailer mailer;

    @Inject
    @IgnoreInJacocoGeneratedReport(reason = "Constructor ignored as local smtp would not be possible")
    public NotificationMailSender(
            MailNotificationConfig mailConfig,
            EventMailDataConverter mailDataConverter) {
        this(mailConfig,
             mailDataConverter,
             MailerBuilder
                     .withSMTPServerHost(mailConfig.getSmtpServer())
                     .withSMTPServerPort(mailConfig.getPort())
                     .withSMTPServerUsername(mailConfig.getUsername())
                     .withSMTPServerPassword(mailConfig.getPassword())
                     .withTransportStrategy(mailConfig.isTls()
                                            ? TransportStrategy.SMTP_TLS
                                            : TransportStrategy.SMTP)
                     .buildMailer());
    }

    @VisibleForTesting
    NotificationMailSender(
            MailNotificationConfig mailConfig,
            EventMailDataConverter mailDataConverter,
            Mailer mailer) {
        this.mailDataConverter = mailDataConverter;
        this.mailConfig = mailConfig;
        this.mailer = mailer;
    }

    @Override
    public void consume(EpochEvent epochEvent) {
        epochEvent.accept((EpochEventVisitor<Void>) stateChangeEvent -> {
            if (NotificationUtils.mailToBeSkipped(stateChangeEvent, mailConfig::isDisableForSuccessfulRuns)) {
                return null;
            }
            mailDataConverter.convert(stateChangeEvent)
                    .ifPresent(this::sendMail);
            return null;
        });
    }

    private void sendMail(MailData mailData) {
        try {
            val email = EmailBuilder.startingBlank()
                    .toMultiple(mailData.emailIds())
                    .withSubject(mailData.subject())
                    .withPlainText(mailData.body())
                    .from("santanu.sinha@gmail.com")
                    .buildEmail();
            mailer.sendMail(email);
            log.info("Mail subject: {}, Body: {}", mailData.subject(), mailData.body());
        }
        catch (Exception e) {
            log.error("Error sending notification email: ", e);
        }
    }
}
