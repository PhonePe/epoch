package com.phonepe.epoch.server.managed;

import com.phonepe.epoch.models.state.EpochTopologyRunState;
import com.phonepe.epoch.models.topology.EpochTopologyRunInfo;
import com.phonepe.epoch.models.triggers.EpochTaskTrigger;
import com.phonepe.epoch.server.execution.ExecuteCommand;
import com.phonepe.epoch.server.execution.ExecutionTimeCalculator;
import com.phonepe.epoch.server.execution.TopologyExecutor;
import com.phonepe.epoch.server.store.TopologyStore;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@Singleton
@Order(100)
public final class Scheduler implements Managed {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");


    private final ExecutorService executorService;
    private final TopologyStore topologyStore;
    private final TopologyExecutor topologyExecutor;

    private final PriorityBlockingQueue<ExecuteCommand> tasks
            = new PriorityBlockingQueue<>(1024, Comparator.comparing(ExecuteCommand::getNextExecutionTime));
    private final ExecutionTimeCalculator timeCalculator = new ExecutionTimeCalculator();

    private final AtomicBoolean stopped = new AtomicBoolean();
    private final ConsumingFireForgetSignal<TaskData> taskCompleted = new ConsumingFireForgetSignal<>();

    private Future<?> monitorFuture = null;

    public void cancelAll() {

    }

    public record TaskData(String topologyId, EpochTopologyRunInfo runInfo, boolean inRecoveryContext) { }

    @Inject
    public Scheduler(@Named("taskPool") ExecutorService executorService,
                     TopologyStore topologyStore,
                     TopologyExecutor topologyExecutor) {
        this.executorService = executorService;
        this.topologyStore = topologyStore;
        this.topologyExecutor = topologyExecutor;
    }

    @Override
    public void start() throws Exception {
        taskCompleted.connect(this::handleTaskCompletion);
        monitorFuture = executorService.submit(this::check);
        log.info("Started scheduler");
    }

    @Override
    public void stop() throws Exception {
        stopped.set(true);
        if (null != monitorFuture) {
            monitorFuture.get();
        }
        log.info("Monitor shut down");
    }

    public ConsumingFireForgetSignal<TaskData> taskCompleted() {
        return taskCompleted;
    }

    public boolean schedule(final String topologyId,
                            final EpochTaskTrigger trigger,
                            Date currTime) {
        val duration = timeCalculator.executionTime(trigger, currTime)
                .map(Duration::toMillis)
                .orElse(-1L);
        if (-1 == duration) {
            return false;
        }
        val executionTime = new Date(currTime.getTime() + duration);
        val runId = "ET-" + DATE_FORMAT.format(executionTime);
        tasks.put(new ExecuteCommand(runId,
                                     executionTime,
                                     topologyId,
                                     false));
        log.debug("Scheduled task {}/{} with delay of {} ms at {}. Reference time: {}",
                  topologyId,
                  runId,
                  duration,
                  executionTime,
                  currTime);
        return true;
    }

    public boolean recover(String topologyId, String runId, Date currTime, long duration) {
        tasks.put(new ExecuteCommand(runId,
                                     new Date(currTime.getTime() + duration),
                                     topologyId,
                                     true));
        log.trace("Scheduled task {}/{} with delay of {} at {}",
                  topologyId,
                  runId,
                  duration,
                  new Date(currTime.getTime() + duration));
        return true;
    }

    private void check() {
        var done = false;
        while (!done) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                log.warn("Monitor thread interrupted");
                Thread.currentThread().interrupt();
                return;
            }
            if (stopped.get()) {
                log.info("Stop called. Exiting monitor thread");
                return;
            }
            processQueuedTask();
        }
        log.info("Exiting monitor thread");
    }

    private void processQueuedTask() {
        while (true) {
            val currTime = new Date();
            val executeCommand = tasks.peek();
            var canContinue = false;
            if (executeCommand == null) {
                log.trace("Nothing queued... will sleep again");
            }
            else {
                log.trace("pulled exec command for: {}", executeCommand.getTopologyId());

                if (currTime.before(executeCommand.getNextExecutionTime())) {
                    log.trace("Found non-executable earliest task: {}/{}",
                              executeCommand.getTopologyId(),
                              executeCommand.getRunId());
                }
                else {
                    canContinue = true;
                }
            }
            if (!canContinue) {
                log.trace("Nothing to do now, will try again later");
                break;
            }
            try {
                executorService.submit(() -> taskCompleted.dispatch(
                        new TaskData(executeCommand.getTopologyId(),
                                     topologyExecutor.execute(executeCommand).orElse(null),
                                     executeCommand.isInRecoveryContext())));
                tasks.remove(executeCommand);
            }
            catch (Exception e) {
                log.error("Error scheduling topology task: ", e);
            }
        }
    }

    private void handleTaskCompletion(final TaskData taskData) {
        val tId = taskData.topologyId();
        val rId = taskData.runInfo().getRunId();

        val result = taskData.runInfo().getState();
        if(taskData.inRecoveryContext()) {
            log.info("Run was recovered. Run {}/{} finished with state: {}", tId, rId, result);
            return;
        }
        if (result == EpochTopologyRunState.COMPLETED) {
            log.info("No further scheduling needed for {}/{}", tId, rId);
            return;
        }
        val trigger = topologyStore.get(tId)
                .map(e -> e.getTopology().getTrigger())
                .orElse(null);
        if(trigger == null) {
            log.info("Topology {} seems to have been deleted, no scheduling needed.", tId);
            return;
        }
        log.debug("{} state for {}/{}. Will try to reschedule for next slot.", result, tId, rId);
        if (!schedule(tId, trigger, new Date())) {
            log.warn("Further scheduling skipped for: {}", tId);
        }
    }


}
