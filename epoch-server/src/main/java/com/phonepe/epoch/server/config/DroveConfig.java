package com.phonepe.epoch.server.config;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.dropwizard.util.Duration;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Data
public class DroveConfig {
    @NotEmpty
    private List<String> endpoints;

    boolean insecure;

    private Duration checkInterval;

    private Duration connectionTimeout;

    private Duration operationTimeout;

    private String username;
    private String password;

    private int rpcRetryCount;

    private Duration rpcRetryInterval;

    @Valid
    private ClusterOpSpec clusterOpSpec;
}
