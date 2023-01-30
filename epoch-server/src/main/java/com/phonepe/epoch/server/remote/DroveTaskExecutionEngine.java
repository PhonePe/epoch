package com.phonepe.epoch.server.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.epoch.models.tasks.EpochContainerExecutionTask;
import com.phonepe.epoch.models.topology.EpochTaskRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunTaskInfo;
import com.phonepe.epoch.server.execution.TaskStatusData;
import com.phonepe.epoch.server.managed.DroveClientManager;
import com.phonepe.epoch.server.utils.EpochUtils;
import io.appform.functionmetrics.MonitoredFunction;
import io.dropwizard.util.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
        this.appName = EpochUtils.appName();
    }

    @Override
    @SneakyThrows
    @MonitoredFunction
    public EpochTopologyRunTaskInfo start(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {

        val instanceId = instanceId(context);
        val upstreamTaskId = context.getUpstreamTaskId();
        if (!Strings.isNullOrEmpty(upstreamTaskId)
                && !upstreamTaskId.equalsIgnoreCase(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID)) {
            return readStatusForExistingTask(context, instanceId, upstreamTaskId);
        }
        val client = droveClientManager.getClient();
        val createOperation = buildTaskCreateOperation(context, executionTask);
        val taskId = taskId(createOperation);
        val url = "/apis/v1/tasks/operations";
        val request = new DroveClient.Request(DroveClient.Method.POST,
                                              url,
                                              mapper.writeValueAsString(createOperation));

        val errorMessage = new AtomicReference<String>();

        try {
            val response = client.execute(request);
            log.trace("Received create response: {}", response);
            if (response != null) {
                if (response.statusCode() == 200) {
                    val apiResponse = mapper.readValue(response.body(),
                                                       new TypeReference<ApiResponse<Map<String, String>>>() {
                                                       });
                    if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                        val droveInternalId = apiResponse.getData()
                                .getOrDefault("taskId", EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID);
                        log.info("Task {}/{}/{} started on drove with taskId: {} and drove internal ID: {}",
                                 context.getTopologyId(),
                                 context.getRunId(),
                                 context.getTaskName(),
                                 taskId,
                                 droveInternalId);
                        return new EpochTopologyRunTaskInfo()
                                .setTaskId(instanceId)
                                .setState(EpochTaskRunState.STARTING)
                                .setErrorMessage("")
                                .setUpstreamId(taskId);
                    }
                    else {
                        val error = "Drove call failed with error: " + response.body();
                        errorMessage.set(error);
                        log.error(error);
                    }
                }
                else if (response.statusCode() == 400) {
                    val res = mapper.readTree(response.body());
                    val error = res.at("/data/validationErrors/0");
                    if (error.isTextual() && error.asText().contains("Task already exists ")) {

                    /*
                    It is possible that the task has not been picked up for execution on drove yet...
                    so try a few times. If unable, move it to unknown state. which is non-terminal
                     but denotes that something is wrong
                     */
                        return readStatusForExistingTask(context, instanceId, taskId);
                    }
                }
                val error = "Received error from api: [" + response.statusCode() + "] " + response.body();
                errorMessage.set(error);
                log.error(error);
            }
            else {
                log.error("No response received for api call: {}", url);
                errorMessage.set("No response from drove");
            }
        }
        catch (Exception e) {
            val message = EpochUtils.errorMessage(e);
            log.error("Error making http call to " + url + ": " + message, e);
            errorMessage.set("Error making http call to " + url + ": " + message);
        }
        /*
        Denotes that task has not been started
         */
        return new EpochTopologyRunTaskInfo()
                .setTaskId(instanceId)
                .setState(EpochTaskRunState.FAILED)
                .setUpstreamId(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID)
                .setErrorMessage(errorMessage.get());
    }

    @Override
    @MonitoredFunction
    public TaskStatusData status(TaskExecutionContext context, EpochContainerExecutionTask executionTask) {
        /*
        Keep task in unknown state in case we are unable to find the status
         */
        return readTaskData(context,
                            new TaskStatusData(EpochTaskRunState.UNKNOWN, "Status could not be ascertained. Task might not have started yet"),
                            taskInfo -> mapTaskState(context, taskInfo),
                            e -> new TaskStatusData(EpochTaskRunState.UNKNOWN, "Error getting task status: " + EpochUtils.errorMessage(e)));
    }

    @Override
    @MonitoredFunction
    public boolean cleanup(String upstreamTaskId) {
        val client = droveClientManager.getClient();
        val api = "/apis/v1/tasks/" + appName + "/instances/" + upstreamTaskId;
        log.trace("Calling: {} for status", api);
        val request = new DroveClient.Request(DroveClient.Method.DELETE,
                                              api);
        try {
            return client.execute(request, new DroveClient.ResponseHandler<>() {
                @Override
                public Boolean defaultValue() {
                    return false;
                }

                @Override
                public Boolean handle(DroveClient.Response response) throws Exception {
                    val body = response.body();
                    if (response.statusCode() == 200) {
                        val apiResponse = mapper.readValue(body,
                                                           new TypeReference<ApiResponse<Map<String, Boolean>>>() {
                                                           });
                        if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                            return apiResponse.getData().getOrDefault("deleted", false);
                        }
                    }
                    log.error("Failed to delete task meta from drove using api {}. Response: [{}] {}",
                              api, response.statusCode(), body);
                    return false;
                }
            });

        }
        catch (Exception e) {
            log.error("Error making http call to " + api + ": " + e.getMessage(), e);
        }
        return false;
    }

    private static String taskId(TaskCreateOperation createOperation) {
        return createOperation.accept(new TaskOperationVisitor<>() {
            @Override
            public String visit(TaskCreateOperation create) {
                return create.getSpec().getTaskId();
            }

            @Override
            public String visit(TaskKillOperation kill) {
                return kill.getTaskId();
            }
        });
    }

    private static TaskStatusData mapTaskState(TaskExecutionContext context, TaskInfo taskInfo) {
        if (null == taskInfo) {
            return null;
        }
        val currState = taskInfo.getState();
        log.debug("State for task {}/{}/{} is: {}",
                  context.getTopologyId(),
                  context.getRunId(),
                  context.getTaskName(),
                  currState);
        val state = switch (currState) {
            case PENDING, PROVISIONING, STARTING -> EpochTaskRunState.STARTING;
            case RUNNING, RUN_COMPLETED, DEPROVISIONING -> EpochTaskRunState.RUNNING;
            case PROVISIONING_FAILED, LOST -> EpochTaskRunState.FAILED;
            case STOPPED -> {
                val taskResult = taskInfo.getTaskResult();
                if (taskResult == null) {
                    yield EpochTaskRunState.COMPLETED;
                }
                yield switch (taskResult.getStatus()) {
                    case SUCCESSFUL -> EpochTaskRunState.COMPLETED;
                    case TIMED_OUT, CANCELLED, FAILED, LOST -> EpochTaskRunState.FAILED;
                };
            }
            case UNKNOWN -> EpochTaskRunState.UNKNOWN;
        };
        return new TaskStatusData(state, taskInfo.getErrorMessage());
    }

    private EpochTopologyRunTaskInfo readStatusForExistingTask(
            TaskExecutionContext context,
            String instanceId,
            String taskId) {
        log.info("Fetching data for existing task");
        val retryPolicy = new RetryPolicy<EpochTopologyRunTaskInfo>()
                .withDelay(Duration.ofSeconds(3))
                .withMaxRetries(10)
                .onFailedAttempt(attempt -> log.debug("Status read attempt {}: {}",
                                                      attempt.getAttemptCount(),
                                                      attempt))
                .handle(Exception.class)
                .handleResultIf(r -> r == null
                        || Strings.isNullOrEmpty(r.getUpstreamId())
                        || r.getState().equals(EpochTaskRunState.UNKNOWN)
                        || r.getUpstreamId().equals(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID));
        val upstreamId = Strings.isNullOrEmpty(context.getUpstreamTaskId())
                         ? EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID
                         : context.getUpstreamTaskId();
        val defaultResponse = new EpochTopologyRunTaskInfo()
                .setTaskId(instanceId)
                .setUpstreamId(upstreamId)
                .setState(EpochTaskRunState.UNKNOWN)
                .setErrorMessage("Could not ascertain if task has started or not." +
                                         " Please check drove logs for status");
        try {
            val taskData = Failsafe.with(List.of(retryPolicy))
                    .get(() -> readTaskData(context,
                                            defaultResponse,
                                            taskInfo -> null != taskInfo
                                                        ? new EpochTopologyRunTaskInfo()
                                                                .setTaskId(instanceId)
                                                                .setUpstreamId(taskInfo.getTaskId())
                                                                .setState(mapTaskState(context,
                                                                                       taskInfo).state())
                                                                .setErrorMessage(taskInfo.getErrorMessage())
                                                        : null,
                                            e -> new EpochTopologyRunTaskInfo()
                                                    .setTaskId(instanceId)
                                                    .setUpstreamId(EpochTopologyRunTaskInfo.UNKNOWN_TASK_ID)
                                                    .setState(EpochTaskRunState.UNKNOWN)
                                                    .setErrorMessage("Error fetching task data: " + e.getMessage())));
            log.info("Task {}/{}/{} already running on drove with taskId: {}",
                     context.getTopologyId(),
                     context.getRunId(),
                     context.getTaskName(),
                     taskData.getUpstreamId());
            return taskData;
        }
        catch (FailsafeException e) {
            val message = EpochUtils.errorMessage(e);
            log.error("Failed to get status for {}: {}", taskId, message);
            return defaultResponse;
        }
    }

    private <T> T readTaskData(
            final TaskExecutionContext context,
            T defaultValue,
            Function<TaskInfo, T> mutator,
            Function<Exception, T> errorHandler) {
        val client = droveClientManager.getClient();
        val instanceId = instanceId(context);
        val api = "/apis/v1/tasks/" + appName + "/instances/" + instanceId;
        val request = new DroveClient.Request(DroveClient.Method.GET, api);
        try {
            val response = client.execute(request);
            log.trace("Received status response: {}", response);
            if (null != response) {
                if (response.statusCode() == 200) {
                    val apiResponse = mapper.readValue(response.body(),
                                                       new TypeReference<ApiResponse<TaskInfo>>() {
                                                       });
                    if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                        return mutator.apply(apiResponse.getData());
                    }
                }
                log.error("Received error while calling status api {}: [{}]: {}",
                          api, response.statusCode(), response.body());
            }
            else {
                log.error("No response while calling {}. Please check logs for exceptions.", request.api());
            }
            return defaultValue;
        }
        catch (Exception e) {
            log.error("Error making http call to " + api + ": " + e.getMessage(), e);
            return errorHandler.apply(e);
        }
    }

    private TaskCreateOperation buildTaskCreateOperation(
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
                                       Objects.requireNonNullElse(droveClientManager
                                                                          .getDroveConfig()
                                                                          .getClusterOpSpec(),
                                                                  ClusterOpSpec.DEFAULT));
    }

    private String instanceId(final TaskExecutionContext context) {
        return context.getTopologyId() + "-" + context.getRunId() + "-" + context.getTaskName();
    }
}
