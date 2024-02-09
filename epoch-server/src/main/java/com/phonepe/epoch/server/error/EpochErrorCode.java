package com.phonepe.epoch.server.error;

import lombok.Getter;

import javax.ws.rs.core.Response;

public enum EpochErrorCode {

    SUCCESS(0, "Success", Response.Status.OK),

    /* 1xxx for internal errors */
    INTERNAL_SERVER_ERROR(1000, "Internal Server Error", Response.Status.INTERNAL_SERVER_ERROR),
    WEB_APP_ERROR(1001, "Internal Web App Error", Response.Status.INTERNAL_SERVER_ERROR),
    JSON_SER_ERROR(1002, "Internal serialization Error for ${id}", Response.Status.INTERNAL_SERVER_ERROR),
    JSON_DESER_ERROR(1003, "Internal deserialization Error", Response.Status.INTERNAL_SERVER_ERROR),
    DB_ERROR(1004, "Internal Database error for ${id}", Response.Status.INTERNAL_SERVER_ERROR),

    /* 2xxx for auth and permissions */
    AUTH_ERROR(2000, "Authorization Error", Response.Status.UNAUTHORIZED),
    FORBIDDEN_ERROR(2001, "Forbidden Error", Response.Status.FORBIDDEN),
    NOT_FOUND_ERROR(2002, "Not Found", Response.Status.NOT_FOUND),
    OPERATION_NOT_ALLOWED(2003, "Operation is not allowed", Response.Status.METHOD_NOT_ALLOWED),

    /* 3xxx for validation */
    WRONG_INPUT_ERROR(3000, "Invalid Input", Response.Status.BAD_REQUEST),
    INVALID_INPUT_ID(3001, "Input id invalid ${id}", Response.Status.BAD_REQUEST),
    INPUT_VALIDATION_ERROR(3002, "Validation failed for input: ${field}", Response.Status.BAD_REQUEST),
    TOPOLOGY_ALREADY_EXISTS(3003, "Topology already exists with name: ${name}", Response.Status.CONFLICT),
    TOPOLOGY_NOT_FOUND(3004, "Topology not found: ${id}", Response.Status.NOT_FOUND),

    /* 4xxx for client errors */
    DROVE_CLIENT_FAILURE(4000, "Failed to call drove", Response.Status.INTERNAL_SERVER_ERROR),

    /* 5xxx for start-up errors */
    INIT_ERROR(5000, "Unable to initialize", Response.Status.INTERNAL_SERVER_ERROR);

    @Getter
    private final int code;
    @Getter
    private final String message;

    @Getter
    private final Response.Status responseStatus;

    EpochErrorCode(int code, String message, Response.Status responseStatus) {
        this.code = code;
        this.message = message;
        this.responseStatus = responseStatus;
    }
}
