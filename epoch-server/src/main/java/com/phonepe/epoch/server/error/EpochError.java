package com.phonepe.epoch.server.error;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public class EpochError extends RuntimeException {
    private final EpochErrorCode errorCode;
    private final transient Map<String, Object> context;
    private final transient String parsedMessage;

    public EpochError(EpochErrorCode errorCode, Map<String, Object> context) {
        super(ErrorUtils.message(errorCode, context));
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = super.getMessage();
    }

    public EpochError(EpochErrorCode errorCode, String message, Throwable e,
                      Map<String, Object> context) {
        super(message, e);
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = ErrorUtils.message(errorCode, context);
    }

    public EpochError(EpochErrorCode errorCode, String message,
                      Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = ErrorUtils.message(errorCode, context);
    }

    public EpochError(EpochErrorCode errorCode, Throwable e,
                      Map<String, Object> context) {
        super(ErrorUtils.message(errorCode, context), e);
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = super.getMessage();
    }

    public static EpochError raise(EpochErrorCode errorCode) {
        return new EpochError(errorCode, Collections.emptyMap());
    }

    public static EpochError raise(EpochErrorCode errorCode, Map<String, Object> context) {
        return new EpochError(errorCode, context);
    }

    public static EpochError raise(EpochErrorCode errorCode, Exception e) {
        return new EpochError(errorCode, e, Collections.emptyMap());
    }

    public static EpochError propagate(Throwable e) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(EpochErrorCode.INTERNAL_SERVER_ERROR, e.getMessage(), e,
                Collections.emptyMap());
    }

    public static EpochError propagate(Throwable e, Map<String, Object> context) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(EpochErrorCode.INTERNAL_SERVER_ERROR, e.getMessage(), e, context);
    }

    public static EpochError propagate(String message, Throwable e) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(EpochErrorCode.INTERNAL_SERVER_ERROR, message + " Error:" + e.getMessage(), e,
                Collections.emptyMap());
    }

    public static EpochError propagate(EpochErrorCode errorCode, Throwable e) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(errorCode, " Error:" + e.getMessage(), e, Collections.emptyMap());
    }
}
