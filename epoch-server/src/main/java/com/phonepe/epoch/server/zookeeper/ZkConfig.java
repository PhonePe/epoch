package com.phonepe.epoch.server.zookeeper;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Data
public class ZkConfig {
    @NotEmpty
    private String connectionString;

    private String nameSpace;
}
