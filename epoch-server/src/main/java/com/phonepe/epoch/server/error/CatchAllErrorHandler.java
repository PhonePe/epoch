package com.phonepe.epoch.server.error;

import com.phonepe.drove.models.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 *
 */
@Provider
@Slf4j
public class CatchAllErrorHandler implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception exception) {
        log.error("Server error: ", exception);
        Throwable rootCause = ExceptionUtils.getRootCause(exception);
        if (rootCause != null) {
            return Response.serverError().entity(ApiResponse.failure(Map.of("failure", rootCause.getMessage()),
                    "Unexpected Server error")).build();
        }
        return Response.serverError().entity(ApiResponse.failure(Map.of("failure", exception.getMessage()),
                                                                 "Unexpected Server error")).build();
    }
}
