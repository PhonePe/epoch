package com.phonepe.epoch.server.notify;

import com.phonepe.epoch.server.event.EpochEvent;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class NotificationBlackholeSender implements NotificationSender {
    @Override
    public void consume(EpochEvent data) {
        log.debug("Ignoring event of type: {}", data.getType());
    }
}
