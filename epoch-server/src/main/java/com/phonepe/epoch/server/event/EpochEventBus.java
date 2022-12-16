package com.phonepe.epoch.server.event;

import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;

import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class EpochEventBus {
    private final ConsumingFireForgetSignal<EpochEvent> eventGenerated = new ConsumingFireForgetSignal<>();

    @MonitoredFunction
    public final void publish(final EpochEvent event) {
        eventGenerated.dispatch(event);
    }

    public final ConsumingFireForgetSignal<EpochEvent> onNewEvent() {
        return eventGenerated;
    }
}
