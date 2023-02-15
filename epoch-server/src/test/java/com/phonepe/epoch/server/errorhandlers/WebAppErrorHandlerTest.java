package com.phonepe.epoch.server.errorhandlers;

import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class WebAppErrorHandlerTest {
    @Test
    void testError() {
        try (val response = new WebAppErrorHandler()
                .toResponse(new WebApplicationException(Response.serverError().build()))) {
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                         response.getStatus());
        }
    }
}