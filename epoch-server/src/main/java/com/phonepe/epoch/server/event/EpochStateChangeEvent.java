package com.phonepe.epoch.server.event;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class EpochStateChangeEvent extends EpochEvent<StateChangeEventDataTag> {

    @Builder
    public EpochStateChangeEvent(EpochEventType type, Map<StateChangeEventDataTag, Object> metadata) {
        super(type, metadata);
    }
}
