package com.phonepe.epoch.server.error;

import lombok.Getter;
import org.apache.commons.text.StringSubstitutor;

import java.util.Collections;
import java.util.Map;

@Getter
public class EpochError extends RuntimeException {
    private final EpochErrorCode errorCode;
    private final transient Map<String, Object> context;
    private final transient String parsedMessage;

    public EpochError(final EpochErrorCode errorCode,
                      final Map<String, Object> context) {
        super(message(errorCode, context));
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = super.getMessage();
    }

    public EpochError(final EpochErrorCode errorCode,
                      final String message,
                      final Throwable e,
                      final Map<String, Object> context) {
        super(message, e);
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = message(errorCode, context);
    }

    public EpochError(final EpochErrorCode errorCode,
                      final Throwable e,
                      final Map<String, Object> context) {
        super(message(errorCode, context), e);
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = super.getMessage();
    }

    public static EpochError raise(final EpochErrorCode errorCode) {
        return new EpochError(errorCode, Collections.emptyMap());
    }

    public static EpochError raise(final EpochErrorCode errorCode,
                                   final Map<String, Object> context) {
        return new EpochError(errorCode, context);
    }

    public static EpochError raise(final EpochErrorCode errorCode,
                                   final Exception e) {
        return new EpochError(errorCode, e, Collections.emptyMap());
    }

    public static EpochError propagate(final Throwable e) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(EpochErrorCode.INTERNAL_SERVER_ERROR, e.getMessage(), e,
                Collections.emptyMap());
    }

    public static EpochError propagate(final Throwable e,
                                       final Map<String, Object> context) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(EpochErrorCode.INTERNAL_SERVER_ERROR, e.getMessage(), e, context);
    }

    public static EpochError propagate(final String message,
                                       final Throwable e) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(EpochErrorCode.INTERNAL_SERVER_ERROR, message + " Error:" + e.getMessage(), e,
                Collections.emptyMap());
    }

    public static EpochError propagate(final EpochErrorCode errorCode,
                                       final Throwable e) {
        if (e instanceof EpochError epochError) {
            return epochError;
        }
        return new EpochError(errorCode, " Error:" + e.getMessage(), e, Collections.emptyMap());
    }

    private static String message(EpochErrorCode errorCode, Map<String, Object> context) {
        return String.format("[%d]: %s", errorCode.getCode(),
                new StringSubstitutor(context).replace(errorCode.getMessage()));
    }
}
