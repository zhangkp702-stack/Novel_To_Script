package com.zkp.my12306.ntc.llm.routing;

import com.zkp.my12306.ntc.llm.config.AIModelProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ModelHealthStore {

    private final AIModelProperties properties;
    private final Map<String, ModelHealth> healthById = new ConcurrentHashMap<>();

    public ModelHealthStore(AIModelProperties properties) {
        this.properties = properties;
    }

    public boolean isUnavailable(String id) {
        ModelHealth health = healthById.get(id);
        if (health == null) {
            return false;
        }
        if (health.state == State.OPEN && health.openUntil > System.currentTimeMillis()) {
            return true;
        }
        return health.state == State.HALF_OPEN && health.halfOpenInFlight;
    }

    public boolean allowCall(String id) {
        if (id == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthById.compute(id, (key, health) -> {
            if (health == null) {
                health = new ModelHealth();
            }
            if (health.state == State.OPEN) {
                if (health.openUntil > now) {
                    return health;
                }
                health.state = State.HALF_OPEN;
                health.halfOpenInFlight = true;
                allowed.set(true);
                return health;
            }
            if (health.state == State.HALF_OPEN) {
                if (health.halfOpenInFlight) {
                    return health;
                }
                health.halfOpenInFlight = true;
                allowed.set(true);
                return health;
            }
            allowed.set(true);
            return health;
        });
        return allowed.get();
    }

    public void markSuccess(String id) {
        if (id == null) {
            return;
        }
        healthById.compute(id, (key, health) -> {
            if (health == null) {
                return new ModelHealth();
            }
            health.state = State.CLOSED;
            health.consecutiveFailures = 0;
            health.openUntil = 0L;
            health.halfOpenInFlight = false;
            return health;
        });
    }

    public void markFailure(String id) {
        if (id == null) {
            return;
        }
        long now = System.currentTimeMillis();
        healthById.compute(id, (key, health) -> {
            if (health == null) {
                health = new ModelHealth();
            }
            if (health.state == State.HALF_OPEN) {
                health.state = State.OPEN;
                health.openUntil = now + properties.getSelection().getOpenDurationMs();
                health.consecutiveFailures = 0;
                health.halfOpenInFlight = false;
                return health;
            }
            health.consecutiveFailures++;
            if (health.consecutiveFailures >= properties.getSelection().getFailureThreshold()) {
                health.state = State.OPEN;
                health.openUntil = now + properties.getSelection().getOpenDurationMs();
                health.consecutiveFailures = 0;
            }
            return health;
        });
    }

    private static class ModelHealth {
        private int consecutiveFailures;
        private long openUntil;
        private boolean halfOpenInFlight;
        private State state = State.CLOSED;
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
