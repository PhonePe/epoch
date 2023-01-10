package com.phonepe.epoch.server.auth.models;

import lombok.Value;

import java.security.Principal;

/**
 *
 */
@Value
public class EpochUser implements Principal {
    public static final EpochUser DEFAULT = new EpochUser("__DEFAULT__", EpochUserRole.READ_WRITE);

    String id;
    EpochUserRole role;

    public EpochUser(String id, EpochUserRole role) {
        this.id = id;
        this.role = role;
    }

    @Override
    public String getName() {
        return id;
    }
}
