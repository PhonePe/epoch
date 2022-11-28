package com.phonepe.epoch.server.config;

import io.dropwizard.util.Duration;
import lombok.Data;

/**
 *
 */
@Data
public class EpochOptionsConfig {
    private Duration cleanupJobInterval;
    private int numRunsPerJob;
}
