package com.phonepe.epoch.server.errorhandlers;

import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class CatchAllErrorHandlerTest {

    @Test
    void test() {
        try (val response = new CatchAllErrorHandler()
                .toResponse(new IllegalStateException("Test error"))) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                         response.getStatus());
        }
    }

}