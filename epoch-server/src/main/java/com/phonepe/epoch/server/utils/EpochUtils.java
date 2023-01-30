package com.phonepe.epoch.server.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.CaseFormat;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.epoch.models.topology.*;
import com.phonepe.epoch.server.managed.Scheduler;
import io.dropwizard.util.Strings;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.ws.rs.core.Response;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 *
 */
@UtilityClass
@Slf4j
public class EpochUtils {
    public static String appName() {
        return Objects.requireNonNullElseGet(System.getenv("DROVE_APP_NAME"),
                                             () -> Objects.requireNonNull(System.getProperty("drove.app.name"),
                                                                          "Provide app name in DROVE_APP_NAME env variable"));
    }

    public static <T> List<T> sublist(final List<T> list, int start, int size) {
        if(list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        val listSize = list.size();
        if(listSize  < start + 1) {
            return Collections.emptyList();
        }
        val end  = Math.min(listSize, start + size);
        return list.subList(start, end);
    }

    public static String topologyId(final EpochTopology topology) {
        return topologyId(topology.getName());
    }

    public static String topologyId(final String topologyName) {
        return topologyName;
    }

    public static EpochTopologyDetails detailsFrom(final EpochTopology topology) {
        return new EpochTopologyDetails(topologyId(topology), topology, EpochTopologyState.ACTIVE, new Date(), new Date());
    }

    public static void scheduleTopology(EpochTopologyDetails topologyDetails, Scheduler scheduler, Date currTime) {
        val runId = scheduler.schedule(topologyDetails.getId(), topologyDetails.getTopology().getTrigger(), currTime);
        if(runId.isPresent()) {
            log.info("Scheduled topology {} for execution with run id: {}", topologyDetails.getId(), runId.get());
        }
        else {
            log.warn("Could not schedule topology {} for execution", topologyDetails.getId());
        }
    }

    @IgnoreInJacocoGeneratedReport(reason = "Not possible to simulate properly")
    private static String readHostname() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            log.error("Error getting hostname: " + e.getMessage(), e);
        }
        return null;
    }

    @IgnoreInJacocoGeneratedReport(reason = "Not possible to simulate properly")
    public static String hostname() {
        val hostname = Objects.requireNonNullElseGet(System.getenv("HOST"), () -> readHostname());
        Objects.requireNonNull(hostname, "Hostname cannot be empty");
        return hostname;
    }

    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public static <T> Response badRequest(T data, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.failure(data, message))
                .build();
    }

    public static Duration getOrDefault(final io.dropwizard.util.Duration incoming) {
        return Duration.ofMillis(Objects.requireNonNullElse(incoming, io.dropwizard.util.Duration.seconds(1))
                                         .toMilliseconds());
    }

    public static Map<String, EpochTopologyRunTaskInfo> addTaskState(final EpochTopologyRunInfo old, String taskName, EpochTaskRunState state, String errorMessage) {
        return updateTaskInfo(old, taskName, info -> info.setState(state).setErrorMessage(errorMessage));
    }
    public static Map<String, EpochTopologyRunTaskInfo> updateTaskInfo(final EpochTopologyRunInfo old, String taskName, Consumer<EpochTopologyRunTaskInfo> updater) {
        val ids = Objects.<Map<String, EpochTopologyRunTaskInfo>>requireNonNullElse(
                old.getTasks(), new HashMap<>());
        ids.compute(taskName, (tName, existing) -> {
            val info = Objects.requireNonNullElse(existing, new EpochTopologyRunTaskInfo());
            updater.accept(info);
            return info;
        });
        return ids;
    }

    public static String errorMessage(Throwable t) {
        var root = t;
        while (null != root.getCause()) {
            root = root.getCause();
        }
        return Strings.isNullOrEmpty(root.getMessage())
                ? CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, root.getClass().getSimpleName())
               : root.getMessage();
    }
}
