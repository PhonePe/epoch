package com.phonepe.epoch.server;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.functionmetrics.FunctionMetricsManager;
import org.junit.jupiter.api.BeforeAll;

import static com.phonepe.epoch.server.utils.EpochUtils.configureMapper;

/**
 *
 */
public class TestBase {
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setup() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("epoch.test", SharedMetricRegistries.getOrCreate("test"));
    }
}
