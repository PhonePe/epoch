package com.phonepe.epoch.server.error;

import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class EpochErrorHandlerTest {

    @Test
    void test() {
        try (val response = new EpochErrorHandler()
                .toResponse(EpochError.raise(EpochErrorCode.INIT_ERROR))) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                         response.getStatus());
        }
        try (val response = new EpochErrorHandler()
                .toResponse(EpochError.raise(EpochErrorCode.INPUT_VALIDATION_ERROR))) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
                         response.getStatus());
        }
    }

}