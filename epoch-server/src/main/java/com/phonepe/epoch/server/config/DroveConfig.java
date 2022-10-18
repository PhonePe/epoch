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

    private Duration checkInterval;

    private Duration connectionTimeout;

    private Duration operationTimeout;

    private String droveAuthToken;

    @Valid
    private ClusterOpSpec clusterOpSpec;
}
