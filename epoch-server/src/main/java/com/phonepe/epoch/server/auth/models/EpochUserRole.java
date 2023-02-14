package com.phonepe.epoch.server.auth.models;

import lombok.Getter;
import lombok.experimental.UtilityClass;

import static com.phonepe.epoch.server.auth.models.EpochUserRole.Values.EPOCH_READ_ONLY_ROLE;
import static com.phonepe.epoch.server.auth.models.EpochUserRole.Values.EPOCH_READ_WRITE_ROLE;

/**
 *
 */
public enum EpochUserRole {
    READ_WRITE(EPOCH_READ_WRITE_ROLE),
    READ_ONLY(EPOCH_READ_ONLY_ROLE),
    ;

    @Getter
    private final String value;

    @UtilityClass
    public static final class Values {
        public static final String EPOCH_READ_WRITE_ROLE = "EPOCH_EXTERNAL_READ_WRITE";
        public static final String EPOCH_READ_ONLY_ROLE = "EPOCH_EXTERNAL_READ_ONLY";
    }

    EpochUserRole(String value) {
        this.value = value;
    }
}
