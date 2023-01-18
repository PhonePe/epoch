package com.phonepe.epoch.server.auth.core;

import com.phonepe.epoch.server.auth.models.EpochUser;
import io.dropwizard.auth.Authorizer;

/**
 *
 */
public class EpochAuthorizer implements Authorizer<EpochUser> {

    @Override
    public boolean authorize(EpochUser epochUser, String role) {
        return epochUser.getRole().getValue().equals(role);
    }
}
