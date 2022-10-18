package com.phonepe.epoch.server.config;

import com.phonepe.epoch.server.zookeeper.ZkConfig;
import io.dropwizard.Configuration;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Data
public class AppConfig extends Configuration {
    @NotNull
    @Valid
    private ZkConfig zookeeper;

    @NotNull
    @Valid
    private DroveConfig drove;

}
