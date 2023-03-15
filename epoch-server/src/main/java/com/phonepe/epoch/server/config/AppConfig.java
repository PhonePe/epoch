package com.phonepe.epoch.server.config;

import com.phonepe.epoch.server.auth.config.BasicAuthConfig;
import com.phonepe.epoch.server.zookeeper.ZkConfig;
import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppConfig extends Configuration {
    @NotNull
    @Valid
    private ZkConfig zookeeper;

    @NotNull
    @Valid
    private DroveConfig drove;

    @Valid
    private BasicAuthConfig userAuth;

    @Valid
    private NotificationConfig notify;

}
