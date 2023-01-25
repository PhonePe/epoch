package com.phonepe.epoch.server.execution;

import com.phonepe.epoch.models.triggers.EpochTaskTriggerAt;
import com.phonepe.epoch.models.triggers.EpochTaskTriggerCron;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class ExecutionTimeCalculatorTest {
    @Test
    void testAtTrigger() {
        val etc = new ExecutionTimeCalculator();
        val date = new Date();
        assertEquals(0,
                     etc.executionTime(new EpochTaskTriggerAt(date), date)
                .map(Duration::toMillis)
                .orElse(-1L));
        assertEquals(0,
                     etc.executionTime(new EpochTaskTriggerAt(date), new Date(date.getTime() + 100))
                .map(Duration::toMillis)
                .orElse(-1L));
    }

    @Test
    void testCronTrigger() {
        val etc = new ExecutionTimeCalculator();
        val date = new Date();
        val delay = etc.executionTime(new EpochTaskTriggerCron("0 * * ? * * *"), new Date(date.getTime() + 100))
                .map(Duration::toMillis)
                .orElse(-1L);
        assertTrue(delay > 0 && delay < 60_000);
    }
}