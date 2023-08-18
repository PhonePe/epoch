package com.phonepe.epoch.models.topology;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RegexUtils {
    public static final String EMAIL_REGEX = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}";
    public static final String EMAILS_REGEX = "^" + EMAIL_REGEX + "(,\\s*" + EMAIL_REGEX + ")*$";
}
