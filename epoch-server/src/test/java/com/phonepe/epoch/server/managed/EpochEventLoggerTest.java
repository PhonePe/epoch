package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.event.StateChangeEventDataTag;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EpochEventLoggerTest {

    @Test
    @SneakyThrows
    void test() {
        val eventBus = new EpochEventBus();
        val el = new EpochEventLogger(eventBus);
        val eventCounter = new AtomicInteger();
        eventBus.onNewEvent().connect(e -> eventCounter.incrementAndGet());
        IntStream.rangeClosed(1, 100)
                .forEach(i -> eventBus.publish(
                        new EpochStateChangeEvent(EpochEventType.TOPOLOGY_STATE_CHANGED,
                                                  Map.of(StateChangeEventDataTag.TOPOLOGY_ID, "T_" + i))));
        el.start();
        assertEquals(100, eventCounter.get());
        el.stop();
    }
}