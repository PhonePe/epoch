package com.phonepe.epoch.server.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.server.managed.DroveClientManager;
import io.appform.functionmetrics.MonitoredFunction;
import io.dropwizard.util.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

import static com.phonepe.epoch.server.utils.EpochUtils.getOrDefault;

/**
 *
 */
@Singleton
@Slf4j
public class DroveTaskExecutionEngine implements TaskExecutionEngine {

    private final DroveClientManager droveClientManager;

    private final String appName;

    private final ObjectMapper mapper;

    @Inject
    public DroveTaskExecutionEngine(DroveClientManager droveClientManager, ObjectMapper mapper) {
        this.droveClientManager = droveClientManager;
        this.mapper = mapper;
        this.appName = Objects.requireNonNull(System.getenv("DROVE_APP_NAME"),
                                              "Provide app name in DROVE_APP_NAME env variable");
    }

    @Override
    @SneakyThrows
    @MonitoredFunction
    public EpochTaskRunState start(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        val client = droveClientManager.getClient();
        val config = droveClientManager.getDroveConfig();

        val url = client.leader().map(host -> host + "/apis/v1/tasks/operations")
                .orElse(null);
        if (Strings.isNullOrEmpty(url)) {
            throw new IllegalStateException("No leader found for drove cluster");
        }
        val requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "O-Bearer " + config.getDroveAuthToken())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        val request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
                        taskCreateOperation(context, executionTask))))
                .timeout(getOrDefault(config.getOperationTimeout()))
                .build();
        try {
            val response = client.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            val body = response.body();
            if (response.statusCode() == 200) {
                val apiResponse = mapper.readValue(body, new TypeReference<ApiResponse<Map<String, String>>>() {
                });
                if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                    log.info("Task {}/{}/{} started on drove with taskId: {}",
                             context.getTopologyId(),
                             context.getRunId(),
                             context.getTaskName(),
                             apiResponse.getData().getOrDefault("taskId", "UNKNOWN"));
                    return EpochTaskRunState.STARTING;
                }
            }
            else if (response.statusCode() == 400) {
                val res = mapper.readTree(body);
                val error = res.at("/data/validationErrors/0");
                if (error.isTextual() && error.asText().contains("Task already exists ")) {
                    log.info("Task {}/{}/{} already running on drove with taskId: {}",
                             context.getTopologyId(),
                             context.getRunId(),
                             context.getTaskName());
                    return EpochTaskRunState.RUNNING;
                }
            }
            throw new IllegalStateException("Received error from api: [" + response.statusCode() + "] " + body);
        }
        catch (IOException e) {
            log.error("Error making http call to " + url + ": " + e.getMessage(), e);
            throw new IllegalStateException("Error making http call to " + url + ": " + e.getMessage(), e);
        }
        catch (InterruptedException e) {
            log.error("HTTP Request interrupted");
            Thread.currentThread().interrupt();
        }
        return EpochTaskRunState.COMPLETED;
    }

    @Override
    @MonitoredFunction
    public EpochTaskRunState status(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        val client = droveClientManager.getClient();
        val config = droveClientManager.getDroveConfig();
        val instanceId = instanceId(context);
        val url = client.leader().map(host -> host + "/apis/v1/tasks/" + appName + "/instances/" + instanceId)
                .orElse(null);
        if (Strings.isNullOrEmpty(url)) {
            throw new IllegalStateException("No leader found for drove cluster");
        }
        log.trace("Calling: {} for status", url);
        val requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "O-Bearer " + config.getDroveAuthToken());
        val request = requestBuilder.GET()
                .timeout(getOrDefault(config.getOperationTimeout()))
                .build();
        try {
            val response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            val body = response.body();
            if (response.statusCode() == 200) {
                val apiResponse = mapper.readValue(body, new TypeReference<ApiResponse<TaskInfo>>() {
                });
                if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                    val currState = apiResponse.getData().getState();
                    log.debug("State for task {}/{}/{} is: {}",
                              context.getTopologyId(),
                              context.getRunId(),
                              context.getTaskName(),
                              currState);
                    return switch (currState) {
                        case PENDING, PROVISIONING, STARTING ->
                                EpochTaskRunState.STARTING;
                        case RUNNING, RUN_COMPLETED, DEPROVISIONING ->
                                EpochTaskRunState.RUNNING;
                        case PROVISIONING_FAILED, LOST -> EpochTaskRunState.FAILED;
                        case STOPPED -> EpochTaskRunState.COMPLETED;
                        case UNKNOWN -> null;
                    };
                }
            }
            throw new IllegalStateException("Received error from api: [" + response.statusCode() + "] " + body);
        }
        catch (IOException e) {
            log.error("Error making http call to " + url + ": " + e.getMessage(), e);
            throw new IllegalStateException("Error making http call to " + url + ": " + e.getMessage(), e);
        }
        catch (InterruptedException e) {
            log.error("HTTP Request interrupted");
            Thread.currentThread().interrupt();
        }
        return EpochTaskRunState.COMPLETED;
    }

    @Override
    @MonitoredFunction
    public boolean cleanup(TaskExecutionContext context, EpochContainerExecutionTask containerExecution) {
        val client = droveClientManager.getClient();
        val config = droveClientManager.getDroveConfig();
        val instanceId = instanceId(context);
        val url = client.leader().map(host -> host + "/apis/v1/tasks/" + appName + "/instances/" + instanceId)
                .orElse(null);
        if (Strings.isNullOrEmpty(url)) {
            throw new IllegalStateException("No leader found for drove cluster");
        }
        log.trace("Calling: {} for status", url);
        val requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .header(HttpHeaders.AUTHORIZATION, "O-Bearer " + config.getDroveAuthToken());
        val request = requestBuilder.DELETE()
                .timeout(getOrDefault(config.getOperationTimeout()))
                .build();
        try {
            val response = client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            val body = response.body();
            if (response.statusCode() == 200) {
                val apiResponse = mapper.readValue(body, new TypeReference<ApiResponse<Map<String, Boolean>>>() {
                });
                if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                    return apiResponse.getData().getOrDefault("deleted", false);
                }
            }
            log.error("Failed to delete task meta from drove. Response: [{}] {}", response.statusCode(), body);
        }
        catch (IOException e) {
            log.error("Error making http call to " + url + ": " + e.getMessage(), e);
            throw new IllegalStateException("Error making http call to " + url + ": " + e.getMessage(), e);
        }
        catch (InterruptedException e) {
            log.error("HTTP Request interrupted");
            Thread.currentThread().interrupt();
        }
        return false;
    }


    private TaskCreateOperation taskCreateOperation(final TaskExecutionContext context,
                                                    final EpochContainerExecutionTask task) {
        return new TaskCreateOperation(new TaskSpec(appName,
                                                    instanceId(context),
                                                    task.getExecutable(),
                                                    task.getVolumes(),
                                                    task.getLogging(),
                                                    task.getResources(),
                                                    task.getPlacementPolicy(),
                                                    task.getTags(),
                                                    task.getEnv()),
                                       Objects.requireNonNullElse(droveClientManager.getDroveConfig()
                                                                          .getClusterOpSpec(),
                                                                  ClusterOpSpec.DEFAULT));
    }

    private String instanceId(final TaskExecutionContext context) {
        return context.getTopologyId() + "-" + context.getRunId() + "-" + context.getTaskName();
    }
}
