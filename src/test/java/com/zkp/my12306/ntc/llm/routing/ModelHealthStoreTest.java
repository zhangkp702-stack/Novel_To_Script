package com.zkp.my12306.ntc.llm.routing;

import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelHealthStoreTest {

    private ModelHealthStore healthStore;

    @BeforeEach
    void setUp() {
        AIModelProperties properties = new AIModelProperties();
        AIModelProperties.Selection selection = new AIModelProperties.Selection();
        selection.setFailureThreshold(2);
        selection.setOpenDurationMs(100L);
        properties.setSelection(selection);
        healthStore = new ModelHealthStore(properties);
    }

    @Test
    void markFailureTwice_shouldOpenCircuit() {
        assertTrue(healthStore.allowCall("model-a"));
        healthStore.markFailure("model-a");
        assertTrue(healthStore.allowCall("model-a"));

        healthStore.markFailure("model-a");
        assertTrue(healthStore.isUnavailable("model-a"));
        assertFalse(healthStore.allowCall("model-a"));
    }

    @Test
    void markSuccess_shouldCloseCircuit() {
        healthStore.markFailure("model-a");
        healthStore.markFailure("model-a");
        assertTrue(healthStore.isUnavailable("model-a"));

        healthStore.markSuccess("model-a");
        assertFalse(healthStore.isUnavailable("model-a"));
        assertTrue(healthStore.allowCall("model-a"));
    }

    @Test
    void openCircuit_shouldRecoverAfterDuration() throws InterruptedException {
        healthStore.markFailure("model-a");
        healthStore.markFailure("model-a");
        assertTrue(healthStore.isUnavailable("model-a"));

        Thread.sleep(150);
        assertFalse(healthStore.isUnavailable("model-a"));
        assertTrue(healthStore.allowCall("model-a"));
    }
}
