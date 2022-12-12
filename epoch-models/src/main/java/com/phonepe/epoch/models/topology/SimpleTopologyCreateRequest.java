package com.phonepe.epoch.models.topology;

import com.phonepe.drove.models.application.MountedVolume;
import lombok.Value;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;
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
    String cron;

    @NotEmpty
    @Pattern(regexp = "^(?:(?=[^:\\/]{4,253})(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(?:\\.(?!-)[a-zA-Z0-9-]{1,63}(?<!-))*" +
            "(?::[0-9]{1,5})?/)?((?![._-])(?:[a-z0-9._-]*)(?<![._-])(?:/(?![._-])[a-z0-9._-]*(?<![._-]))*)(?::(?![" +
            ".-])[a-zA-Z0-9_.-]{1,128})?$")
    String docker;

    @Range(min = 1, max = 40)
    int cpus;
    @Range(min = 128, max = 10240)
    long memory;

    Map<String, String> env;

    List<MountedVolume> volumes;
}
