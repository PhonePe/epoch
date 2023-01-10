package com.phonepe.epoch.server.auth.config;

import com.phonepe.epoch.server.auth.models.EpochUserRole;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Value
public class BasicAuthConfig {
    public static final String DEFAULT_CACHE_POLICY = "maximumSize=500, expireAfterAccess=30m";
    public static final BasicAuthConfig DEFAULT = new BasicAuthConfig(
            false,
            List.of(new UserAuthInfo("default-user",
                                     "default-password",
                                     EpochUserRole.READ_WRITE)),
            DEFAULT_CACHE_POLICY);
    boolean enabled;

    @NotEmpty
    List<UserAuthInfo> users;

    String cachingPolicy;
}
