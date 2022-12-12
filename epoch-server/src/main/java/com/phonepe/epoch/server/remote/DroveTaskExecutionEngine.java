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
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
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
import java.util.function.Function;

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
    public EpochTopologyRunTaskInfo start(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        if (!Strings.isNullOrEmpty(context.getUpstreamTaskId())
                && !context.getUpstreamTaskId().equalsIgnoreCase(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID)) {
            log.info("Looks like task {}/{}/{} was already submitted with task ID: {}. Will fetch state only.",
                     context.getTopologyId(),
                     context.getRunId(),
                     context.getTaskName(),
                     context.getUpstreamTaskId());
            val taskState = readTaskData(context,
                                         EpochTaskRunState.STARTING,
                                         taskInfo -> mapTaskState(context, taskInfo),
                                         e -> EpochTaskRunState.FAILED);
            return new EpochTopologyRunTaskInfo()
                    .setTaskId(instanceId(context))
                    .setUpstreamId(context.getUpstreamTaskId())
                    .setState(taskState);
        }
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
                    val upstreamTaskID = apiResponse.getData().getOrDefault("taskId",
                                                                            EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
                    log.info("Task {}/{}/{} started on drove with taskId: {}",
                             context.getTopologyId(),
                             context.getRunId(),
                             context.getTaskName(),
                             upstreamTaskID);
                    return new EpochTopologyRunTaskInfo()
                            .setTaskId(instanceId(context))
                            .setState(EpochTaskRunState.STARTING)
                            .setUpstreamId(upstreamTaskID);
                }
            }
            else if (response.statusCode() == 400) {
                val res = mapper.readTree(body);
                val error = res.at("/data/validationErrors/0");
                if (error.isTextual() && error.asText().contains("Task already exists ")) {
                    log.info("Fetching data for existing task");
                    val taskData = this.readTaskData(context,
                                                     new EpochTopologyRunTaskInfo()
                                                             .setTaskId(instanceId(context))
                                                             .setUpstreamId(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID)
                                                             .setState(EpochTaskRunState.STARTING),
                                                     taskInfo -> new EpochTopologyRunTaskInfo()
                                                             .setTaskId(instanceId(context))
                                                             .setUpstreamId(taskInfo.getTaskId())
                                                             .setState(mapTaskState(context, taskInfo)),
                                                     e -> new EpochTopologyRunTaskInfo()
                                                             .setTaskId(instanceId(context))
                                                             .setUpstreamId(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID)
                                                             .setState(EpochTaskRunState.FAILED));
                    log.info("Task {}/{}/{} already running on drove with taskId: {}",
                             context.getTopologyId(),
                             context.getRunId(),
                             context.getTaskName(),
                             taskData.getUpstreamId());
                    return taskData;
                }
            }
            throw new IllegalStateException("Received error from api: [" + response.statusCode() + "] " + body);
        }
        catch (IOException e) {
            log.error("Error making http call to " + url + ": " + e.getMessage(), e);
            return new EpochTopologyRunTaskInfo()
                    .setTaskId(instanceId(context))
                    .setState(EpochTaskRunState.FAILED)
                    .setUpstreamId(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
        }
        catch (InterruptedException e) {
            log.error("HTTP Request interrupted");
            Thread.currentThread().interrupt();
        }
        return new EpochTopologyRunTaskInfo()
                .setTaskId(instanceId(context))
                .setState(EpochTaskRunState.COMPLETED)
                .setUpstreamId(context.getUpstreamTaskId());
    }

    @Override
    @MonitoredFunction
    public EpochTaskRunState status(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        return readTaskData(context, EpochTaskRunState.COMPLETED, taskInfo -> mapTaskState(context, taskInfo), e -> EpochTaskRunState.RUNNING);
    }

    private static EpochTaskRunState mapTaskState(TaskExecutionContext context, TaskInfo taskInfo) {
        val currState = taskInfo.getState();
        log.debug("State for task {}/{}/{} is: {}",
                  context.getTopologyId(),
                  context.getRunId(),
                  context.getTaskName(),
                  currState);
        return switch (currState) {
            case PENDING, PROVISIONING, STARTING -> EpochTaskRunState.STARTING;
            case RUNNING, RUN_COMPLETED, DEPROVISIONING -> EpochTaskRunState.RUNNING;
            case PROVISIONING_FAILED, LOST -> EpochTaskRunState.FAILED;
            case STOPPED -> EpochTaskRunState.COMPLETED;
            case UNKNOWN -> null;
        };
    }

    @Override
    @MonitoredFunction
    public boolean cleanup(String upstreamTaskId) {
        val client = droveClientManager.getClient();
        val config = droveClientManager.getDroveConfig();
        val url = client.leader().map(host -> host + "/apis/v1/tasks/" + appName + "/instances/" + upstreamTaskId)
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

    private <T> T readTaskData(
            final TaskExecutionContext context,
            T defaultValue,
            Function<TaskInfo, T> mutator,
            Function<Exception, T> errorHandler) {
        val client = droveClientManager.getClient();
        val config = droveClientManager.getDroveConfig();
        val instanceId = instanceId(context);
        val url = client.leader().map(host -> host + "/apis/v1/tasks/" + appName + "/instances/" + instanceId)
                .orElse(null);
        if (Strings.isNullOrEmpty(url)) {
            throw new IllegalStateException("No leader found for drove cluster");
        }
        log.debug("Calling: {} for status", url);
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
                    return mutator.apply(apiResponse.getData());
                }
            }
            throw new IllegalStateException("Received error from api: [" + response.statusCode() + "] " + body);
        }
        catch (IOException e) {
            log.error("Error making http call to " + url + ": " + e.getMessage(), e);
            return errorHandler.apply(e);
        }
        catch (InterruptedException e) {
            log.error("HTTP Request interrupted");
            Thread.currentThread().interrupt();
        }
        return defaultValue;
    }

    private TaskCreateOperation taskCreateOperation(
            final TaskExecutionContext context,
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
