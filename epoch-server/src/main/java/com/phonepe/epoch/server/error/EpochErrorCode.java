package com.phonepe.epoch.server.error;

import lombok.Getter;

public enum EpochErrorCode {

    SUCCESS(0, "Success", 200),

    /* 1xxx for internal errors */
    INTERNAL_SERVER_ERROR(1000, "Internal Server Error", 500),
    WEB_APP_ERROR(1001, "Internal Web App Error", 500),
    JSON_SER_ERROR(1002, "Internal serialization Error for ${id}", 500),
    JSON_DESER_ERROR(1003, "Internal deserialization Error", 500),
    DB_ERROR(1004, "Internal Database error for ${id}", 500),

    /* 2xxx for auth and permissions */
    AUTH_ERROR(2000, "Authorization Error", 401),
    FORBIDDEN_ERROR(2001, "Forbidden Error", 403),
    NOT_FOUND_ERROR(2002, "Not Found", 404),
    OPERATION_NOT_ALLOWED(2003, "Operation is not allowed", 405),

    /* 3xxx for validation */
    WRONG_INPUT_ERROR(3000, "Invalid Input", 400),
    INVALID_INPUT_ID(3001, "Input id invalid ${id}", 400),
    INPUT_VALIDATION_ERROR(3002, "Validation failed for input: ${field}", 400),
    TOPOLOGY_ALREADY_EXISTS(3003, "Topology already exists with name: ${name}", 409),
    TOPOLOGY_NOT_FOUND(3004, "Topology not found: ${id}", 409),

    /* 4xxx for client errors */
    DROVE_CLIENT_FAILURE(4000, "Failed to call drove", 500),

    /* 5xxx for start-up errors */
    INIT_ERROR(5000, "Unable to initialize", 500);

    @Getter
    private final int code;
    @Getter
    private final String message;

    @Getter
    private final int httpStatusCode;

    EpochErrorCode(int code, String message, int httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
