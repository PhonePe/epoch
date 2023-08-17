package com.phonepe.epoch.models.topology;

import com.phonepe.drove.models.application.MountedVolume;
import lombok.Value;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Value
public class SimpleTopologyCreateRequest {
    @NotEmpty
    @Pattern(regexp = "[0-9a-zA-Z_-]+")
    String name;

    @NotEmpty
    @SuppressWarnings({"java:S5843", "java:S5998"})
    @Pattern(regexp = "^\\s*($|#|\\w+\\s*=|(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:"
                      + "(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,"
                      + "(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[01]?\\d|2[0-3])(?:"
                      + "(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?(?:,(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3])"
                      + ")?)*)\\s+(\\?|\\*|(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?(?:,"
                      + "(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?)*)\\s+(\\?|\\*|"
                      + "(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?(?:,(?:[1-9]|1[012])(?:"
                      + "(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?)*|\\?|\\*|"
                      + "(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)"
                      + "(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?(?:,"
                      + "(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)"
                      + "(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?)*)\\s+(\\?|\\*|(?:[0-6])(?:"
                      + "(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?(?:,(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?)*|\\?|\\*|"
                      + "(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?(?:,"
                      + "(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?)*)(|\\s)+(\\?|\\*|"
                      + "(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?(?:,(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?)*))$",
            message = "Invalid cron, please check https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html for help")
    String cron;

    @NotEmpty
    @Pattern(regexp = "^(?:(?=[^:\\/]{4,253})(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(?:\\.(?!-)[a-zA-Z0-9-]{1,63}(?<!-))*" +
                      "(?::[0-9]{1,5})?/)?((?![._-])(?:[a-z0-9._-]*)(?<![._-])(?:/(?![._-])[a-z0-9._-]*(?<![._-]))*)"
                      + "(?::(?![.-])[a-zA-Z0-9_.-]{1,128})?$")
    @SuppressWarnings({"java:S5843", "java:S5998"}) //Regex complexity and size ... it is what it is ... such is life
    @Length(max = 2048)
    String docker;

    @Range(min = 1, max = 40)
    int cpus;
    @Range(min = 128, max = 100000)
    long memory;

    @NotNull
    @Email
    String notifyEmail;

    Map<String, String> env;

    List<MountedVolume> volumes;
}
