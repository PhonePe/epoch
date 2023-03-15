package com.phonepe.epoch.server.notify;

import com.phonepe.epoch.server.event.EpochEvent;
import io.appform.signals.signalhandlers.SignalConsumer;

/**
 *
 */
public interface NotificationSender extends SignalConsumer<EpochEvent> {
}
