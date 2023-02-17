package com.phonepe.epoch.server.auth.core;


import com.phonepe.epoch.server.auth.config.BasicAuthConfig;
import com.phonepe.epoch.server.auth.config.UserAuthInfo;
import com.phonepe.epoch.server.auth.models.EpochUser;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Optional;


/**
 *
 */
public class EpochAuthenticator implements Authenticator<BasicCredentials, EpochUser> {
    private final BasicAuthConfig basicAuthConfig;

    public EpochAuthenticator(BasicAuthConfig basicAuthConfig) {
        this.basicAuthConfig = basicAuthConfig;
    }

    @Override
    public Optional<EpochUser> authenticate(BasicCredentials credentials) {
        return basicAuthConfig.getUsers()
                .stream()
                .filter(user -> user.getUsername().equals(credentials.getUsername())
                        && user.getPassword().equals(credentials.getPassword()))
                .map(this::toEpochUser)
                .findAny();
    }

    private EpochUser toEpochUser(UserAuthInfo user) {
        return new EpochUser(user.getUsername(), user.getRole());
    }
}
