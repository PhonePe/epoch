package com.phonepe.epoch.models.topology;

import com.phonepe.drove.models.application.MountedVolume;
import lombok.Value;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

@Value
public class SimpleTopologyEditRequest {

    @NotEmpty
    String cron;

    @NotEmpty
    @Pattern(regexp = Regexes.DOCKER_REGEX)
    @Length(max = 2048)
    String docker;

    @Range(min = 1, max = 40)
    int cpus;
    @Range(min = 128, max = 100000)
    long memory;

    @NotNull
    @Pattern(regexp = Regexes.EMAILS_REGEX, message = "Invalid email(s) format")
    String notifyEmail;

    Map<String, String> env;

    List<MountedVolume> volumes;
}
