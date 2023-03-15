package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.server.TestBase;
import com.phonepe.epoch.server.event.EpochEventBus;
import com.phonepe.epoch.server.event.EpochEventType;
import com.phonepe.epoch.server.event.EpochStateChangeEvent;
import com.phonepe.epoch.server.notify.NotificationSender;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.epoch.server.TestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class NotificationManagerTest extends TestBase {

    @SneakyThrows
    @Test
    void test() {
        val eb = new EpochEventBus();
        val ns = mock(NotificationSender.class);
        val nm = new NotificationManager(ns, eb);
        val flag = new AtomicBoolean();

        when(ns.handle(any())).thenAnswer(invocationOnMock -> {
            flag.set(true);
            return null;
        });
        nm.start();
        eb.publish(new EpochStateChangeEvent(EpochEventType.TOPOLOGY_STATE_CHANGED, Map.of()));
        waitUntil(flag::get);
        assertTrue(flag.get());
        nm.stop();
    }

}