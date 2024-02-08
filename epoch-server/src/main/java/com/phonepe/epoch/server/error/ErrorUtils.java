package com.phonepe.epoch.server.error;

import lombok.experimental.UtilityClass;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;

@UtilityClass
public class ErrorUtils {

    public String message(EpochErrorCode errorCode, Map<String, Object> context) {
        return String.format("[%d]: %s", errorCode.getCode(),
                             new StringSubstitutor(context).replace(errorCode.getMessage()));
    }
}
