package com.phonepe.epoch.server.auth.filters;


import com.phonepe.epoch.server.auth.config.BasicAuthConfig;
import com.phonepe.epoch.server.auth.models.EpochUser;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import lombok.val;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Optional;

/**
 *
 */
@Priority(Priorities.AUTHENTICATION)
public class DummyAuthFilter extends AuthFilter<BasicCredentials, EpochUser> {
    public static final class DummyAuthenticator implements Authenticator<BasicCredentials, EpochUser> {

        @Override
        public Optional<EpochUser> authenticate(BasicCredentials credentials) {
            val dummyUser = BasicAuthConfig.DEFAULT.getUsers().get(0);
            return Optional.of(new EpochUser(dummyUser.getUsername(), dummyUser.getRole()));
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if(!authenticate(requestContext, new BasicCredentials("", ""), SecurityContext.BASIC_AUTH)) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    public static class Builder extends
            AuthFilterBuilder<BasicCredentials, EpochUser, DummyAuthFilter> {

        @Override
        protected DummyAuthFilter newInstance() {
            return new DummyAuthFilter();
        }
    }
}
