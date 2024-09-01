package com.phonepe.epoch.server.error;

import com.phonepe.drove.models.api.ApiResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class EpochErrorHandler implements ExceptionMapper<EpochError> {
    @Override
    public Response toResponse(EpochError error) {
        return Response.status(error.getErrorCode().getResponseStatus())
                .entity(ApiResponse.failure(error.getParsedMessage()))
                .build();
    }
}
