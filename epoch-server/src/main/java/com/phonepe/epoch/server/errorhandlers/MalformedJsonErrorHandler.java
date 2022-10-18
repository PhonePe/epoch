package com.phonepe.epoch.server.errorhandlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.epoch.server.utils.EpochUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 *
 */
@Provider
public class MalformedJsonErrorHandler implements ExceptionMapper<JsonProcessingException> {
    @Override
    public Response toResponse(JsonProcessingException exception) {
        return EpochUtils.badRequest(Map.of("validationErrors", exception.getOriginalMessage()),
                                     "JSON validation failure");
    }
}
