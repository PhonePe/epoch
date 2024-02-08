package com.phonepe.epoch.server.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuartzCronUtilityTest {

    @Test
    void testCronValidation() {
        assertTrue(QuartzCronUtility.isValidCronExpression("0 * * ? * * *"));
        assertTrue(QuartzCronUtility.isValidCronExpression("0 * * ? * *"));
        assertFalse(QuartzCronUtility.isValidCronExpression("0 * * ? *"));
        assertFalse(QuartzCronUtility.isValidCronExpression("0 * * ? * * * *"));
        assertFalse(QuartzCronUtility.isValidCronExpression(""));
        assertFalse(QuartzCronUtility.isValidCronExpression(null));
        assertTrue(QuartzCronUtility.isValidCronExpression("0 0 9-20/3 ? * *"));
        assertFalse(QuartzCronUtility.isValidCronExpression("0 0 9-20/3 ? * *1"));
    }
}