package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DrMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    @DisplayName("a plan is answered with what it promises, what the exercises so far say, and how it is judged now")
    void plan() {
        Instant passed = NOW.minusSeconds(86400);
        RecoveryPlan plan = RecoveryPlan.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "Orders", UUID.randomUUID(), Strategy.WARM_STANDBY, 120, 30, 30, "KB-1", PlanStatus.ACTIVE,
                NOW.minusSeconds(86400L * 90), NOW, NOW, Outcome.PARTIAL, passed, passed, 45, 10, List.of());

        RecoveryPlanResponse response = DrMapper.toResponse(plan, NOW);

        assertEquals("WARM_STANDBY", response.strategy());
        assertEquals("KB-1", response.runbookRef());
        assertEquals("LAST_EXERCISE_FAILED", response.readiness());
        assertEquals("PARTIAL", response.lastOutcome());
        assertEquals(passed, response.lastPassedAt());
        assertEquals(45, response.lastMeasuredMinutes());
        assertEquals(10, response.lastMeasuredDataLossMinutes());
        assertEquals(true, response.rtoMet());
        assertEquals(true, response.rpoMet());
    }

    @Test
    @DisplayName("a plan never exercised is answered with no outcome at all")
    void planWithoutExercises() {
        RecoveryPlan plan = RecoveryPlan.createNew(UUID.randomUUID(), UUID.randomUUID(), "New", UUID.randomUUID(), Strategy.ACTIVE_ACTIVE, 5, 0, 30, null, "op");

        RecoveryPlanResponse response = DrMapper.toResponse(plan, Instant.now());

        assertEquals("NEVER_TESTED", response.readiness());
        assertNull(response.lastOutcome());
        assertNull(response.lastExerciseAt());
        assertNull(response.runbookRef());
        assertNull(response.rtoMet());
    }

    @Test
    @DisplayName("the mapper is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<DrMapper> constructor = DrMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}
