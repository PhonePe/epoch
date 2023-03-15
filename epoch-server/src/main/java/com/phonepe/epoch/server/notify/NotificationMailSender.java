package com.phonepe.epoch.server.notify;

import com.phonepe.epoch.models.notification.BlackholeNotificationSpec;
import com.phonepe.epoch.models.notification.MailNotificationSpec;
import com.phonepe.epoch.models.notification.NotificationSpecVisitor;
import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.config.MailNotificationConfig;
import com.phonepe.epoch.server.event.*;
import com.phonepe.epoch.server.store.TopologyRunInfoStore;
import com.phonepe.epoch.server.store.TopologyStore;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
@SuppressWarnings("unchecked")
public class NotificationMailSender implements NotificationSender {
    private final TopologyStore topologyStore;
    private final TopologyRunInfoStore runInfoStore;

    private final MailNotificationConfig mailConfig;
    private final Mailer mailer;

    @Inject
    public NotificationMailSender(
            TopologyStore topologyStore,
            TopologyRunInfoStore runInfoStore,
            MailNotificationConfig mailConfig) {
        this.topologyStore = topologyStore;
        this.runInfoStore = runInfoStore;
        this.mailConfig = mailConfig;
        this.mailer = MailerBuilder
                .withSMTPServerHost(mailConfig.getSmtpServer())
                .withSMTPServerPort(mailConfig.getPort())
                .withSMTPServerUsername(mailConfig.getUsername())
                .withSMTPServerPassword(mailConfig.getPassword())
                .withTransportStrategy(mailConfig.isTls()
                                       ? TransportStrategy.SMTP_TLS
                                       : TransportStrategy.SMTP)
                .buildMailer();
    }

    @Override
    public void consume(EpochEvent epochEvent) {
        epochEvent.accept((EpochEventVisitor<Void>) stateChangeEvent -> {
            handleStateChangeEvent(stateChangeEvent);
            return null;
        });
    }

    private record MailData(String subject, String body) {
    }

    private void handleStateChangeEvent(final EpochStateChangeEvent stateChangeEvent) {
        if (!stateChangeEvent.getType().equals(EpochEventType.TOPOLOGY_RUN_STATE_CHANGED)) {
            log.debug("Ignoring event of type {}", stateChangeEvent.getType());
            return;
        }
        val newState = (EpochTopologyRunState) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.NEW_STATE);
        val topologyId = (String) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_ID);
        val runId = (String) stateChangeEvent.getMetadata().get(StateChangeEventDataTag.TOPOLOGY_RUN_ID);
        val runInfo = runInfoStore.get(topologyId, runId).orElse(null);
        val emailIds = topologyStore.get(topologyId)
                .map(topologyDetails -> topologyDetails.getTopology().getNotify())
                .map(notificationSpec -> notificationSpec.accept(new NotificationSpecVisitor<List<String>>() {
                    @Override
                    public List<String> visit(MailNotificationSpec mailSpec) {
                        return mailSpec.getEmails();
                    }

                    @Override
                    public List<String> visit(BlackholeNotificationSpec blackhole) {
                        return List.of();
                    }
                }))
                .orElseGet(() -> Objects.requireNonNullElse(mailConfig.getDefaultEmails(), List.of()));
        if (emailIds.isEmpty()) {
            log.warn("No mail notification spec provided. Ignoring state change message");
            return;
        }

        buildMailData(newState, topologyId, runId, runInfo).ifPresent(mailData -> sendMail(emailIds, mailData));
    }

    private static Optional<MailData> buildMailData(
            EpochTopologyRunState newState,
            String topologyId,
            String runId,
            EpochTopologyRunInfo runInfo) {
        return switch (newState) {
            case RUNNING, SKIPPED -> Optional.empty();
            case COMPLETED, SUCCESSFUL -> Optional.of(
                    new MailData(
                            String.format("Topology run %s/%s completed successfully", topologyId, runId),
                            String.format("Tasks completed: %s in %d ms",
                                          runInfo.getTasks()
                                                  .values()
                                                  .stream()
                                                  .map(EpochTopologyRunTaskInfo::getTaskId)
                                                  .toList(),
                                          runInfo.getUpdated().getTime() - runInfo.getCreated().getTime())));
            case FAILED -> {
                val failedTask = runInfo.getTasks().values().stream()
                        .filter(taskRun -> taskRun.getState().equals(EpochTaskRunState.FAILED))
                        .findFirst()
                        .orElse(null);
                if (failedTask != null) {
                    yield Optional.of(
                            new MailData(
                                    String.format("Topology run %s/%s failed", topologyId, runId),
                                    String.format("Task %s with upstream ID %s failed with error: %s",
                                                  failedTask.getTaskId(),
                                                  failedTask.getUpstreamId(),
                                                  failedTask.getErrorMessage())));
                }
                yield Optional.empty();
            }
        };
    }

    private void sendMail(List<String> emailIds, MailData mailData) {
        try {
            val email = EmailBuilder.startingBlank()
                    .toMultiple(emailIds)
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
