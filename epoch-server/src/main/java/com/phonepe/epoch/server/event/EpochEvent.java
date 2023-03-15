package com.phonepe.epoch.server.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class EpochEvent<T extends Enum<T>> {
    private final EpochEventType type;
    private final String id = UUID.randomUUID().toString();
    private final Date time = new Date();
    private Map<T, Object> metadata;

    public abstract <T> T accept(final EpochEventVisitor<T> visitor);
}
