package com.phonepe.epoch.server;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    }
}
