package com.phonepe.epoch.server.auth.config;

import com.phonepe.epoch.server.auth.models.EpochUserRole;
import lombok.Value;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
public class UserAuthInfo {
    @NotEmpty
    @Length(min = 1, max = 255)
    String username;

    @NotEmpty
    @Length(min = 1, max = 255)
    String password;

    @NotNull
    EpochUserRole role;
}
