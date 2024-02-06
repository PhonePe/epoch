package com.phonepe.epoch.models.topology;

import lombok.experimental.UtilityClass;

@SuppressWarnings({"java:S5843", "java:S5998"}) //Regex complexity and size ... it is what it is ... such is life
@UtilityClass
public class Regexes {
    public static final String EMAIL_REGEX = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}";

    public static final String EMAILS_REGEX = "^" + EMAIL_REGEX + "(,\\s*" + EMAIL_REGEX + ")*$";

    public static final String DOCKER_REGEX
            = "^(?:(?=[^:\\/]{4,253})(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(?:\\.(?!-)[a-zA-Z0-9-]{1,63}(?<!-))*" +
              "(?::[0-9]{1,5})?/)?((?![._-])(?:[a-z0-9._-]*)(?<![._-])(?:/(?![._-])[a-z0-9._-]*(?<![._-]))*)"
              + "(?::(?![.-])[a-zA-Z0-9_.-]{1,128})?$";

    public static final String TOPOLOGY_NAME_REGEX = "[0-9a-zA-Z_-]+";
}
