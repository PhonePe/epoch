package com.phonepe.epoch.server.config;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class DroveConfig {
    @NotEmpty
    String droveEndpoint;

    String droveAuthToken;

    @NotNull
    @Valid
    ClusterOpSpec clusterOpSpec;
}
