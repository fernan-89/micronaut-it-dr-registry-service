package com.thinklab.domain.model;

import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryExerciseTest {

    private final UUID id = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();
    private final UUID plan = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");
    private final Instant start = Instant.parse("2026-10-07T10:00:00.123456Z");
    private final Instant end = Instant.parse("2026-10-07T10:30:00Z");

    private RecoveryExercise exercise(Outcome outcome, FailureReason reason, Integer dataLoss) {
        return RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, outcome, start, end, dataLoss, reason, "tool", now);
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a successful exercise is a fact: times cut to the millisecond, no reason, duration in minutes, what it lost")
    void succeeded() {
        RecoveryExercise exercise = exercise(Outcome.SUCCEEDED, null, 12);

        assertTrue(exercise.succeeded());
        assertEquals(Instant.parse("2026-10-07T10:00:00.123Z"), exercise.getStartedAt());
        assertEquals(30, exercise.durationMinutes());
        assertEquals(id, exercise.getId());
        assertEquals(org, exercise.getOrganisationId());
        assertEquals(plan, exercise.getPlanId());
        assertEquals(ExerciseKind.FAILOVER, exercise.getKind());
        assertEquals(Outcome.SUCCEEDED, exercise.getOutcome());
        assertEquals(end, exercise.getFinishedAt());
        assertEquals(12, exercise.getDataLossMinutes());
        assertNull(exercise.getFailureReason());
        assertEquals("tool", exercise.getReportedBy());
        assertEquals(now, exercise.getReportedAt());
    }

    @Test
    @DisplayName("a partial or failed exercise carries one reason from the fixed list, and does not count as a pass")
    void notPassed() {
        for (Outcome outcome : new Outcome[]{Outcome.PARTIAL, Outcome.FAILED}) {
            RecoveryExercise exercise = exercise(outcome, FailureReason.RUNBOOK_OUTDATED, null);
            assertFalse(exercise.succeeded());
            assertEquals(FailureReason.RUNBOOK_OUTDATED, exercise.getFailureReason());
            assertNull(exercise.getDataLossMinutes());
        }
    }

    @Test
    @DisplayName("only a failover or a full recovery measures a recovery: a walk through the runbook takes as long as the meeting did")
    void measured() {
        assertFalse(RecoveryExercise.createNew(id, org, plan, ExerciseKind.TABLETOP, Outcome.SUCCEEDED, start, end, null, null, "tool", now).measured());
        assertTrue(RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "tool", now).measured());
        assertTrue(RecoveryExercise.createNew(id, org, plan, ExerciseKind.FULL_RECOVERY, Outcome.SUCCEEDED, start, end, null, null, "tool", now).measured());
    }

    @Test
    @DisplayName("how long the recovery took is whole minutes rounded up and never less than one")
    void duration() {
        Instant t = Instant.parse("2026-10-07T10:00:00Z");
        assertEquals(1, RecoveryExercise.createNew(id, org, plan, ExerciseKind.TABLETOP, Outcome.SUCCEEDED, t, t, null, null, "tool", now).durationMinutes());
        assertEquals(1, RecoveryExercise.createNew(id, org, plan, ExerciseKind.TABLETOP, Outcome.SUCCEEDED, t, t.plusSeconds(60), null, null, "tool", now).durationMinutes());
        assertEquals(2, RecoveryExercise.createNew(id, org, plan, ExerciseKind.TABLETOP, Outcome.SUCCEEDED, t, t.plusSeconds(61), null, null, "tool", now).durationMinutes());
        assertEquals(2, RecoveryExercise.createNew(id, org, plan, ExerciseKind.TABLETOP, Outcome.SUCCEEDED, t, t.plusSeconds(90), null, null, "tool", now).durationMinutes());
    }

    @Test
    @DisplayName("an exercise that does not hold together is refused, and says why")
    void refuses() {
        rejects(() -> RecoveryExercise.createNew(null, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, null, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, null, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, null, Outcome.SUCCEEDED, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, null, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, null, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, null, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, end, start, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.FAILED, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.PARTIAL, start, end, null, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, FailureReason.OTHER, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, -1, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, RecoveryExercise.MAX_DATA_LOSS_MINUTES + 1, null, "tool", now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, null, now));
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, " ", now));
    }

    @Test
    @DisplayName("the data lost may be zero or the largest allowed; the finish may be five minutes ahead of the registry's clock and no more")
    void edges() {
        assertEquals(0, RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, 0, null, "tool", now).getDataLossMinutes());
        assertEquals(RecoveryExercise.MAX_DATA_LOSS_MINUTES, RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end,
                RecoveryExercise.MAX_DATA_LOSS_MINUTES, null, "tool", now).getDataLossMinutes());
        Instant ahead = now.plus(RecoveryExercise.CLOCK_SKEW);
        assertEquals(ahead, RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, ahead, null, null, "tool", now).getFinishedAt());
        rejects(() -> RecoveryExercise.createNew(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, ahead.plusMillis(1), null, null, "tool", now));
    }

    @Test
    @DisplayName("reconstitute needs the identity, the kind, the outcome and the times; the rest may be absent")
    void reconstitute() {
        RecoveryExercise back = RecoveryExercise.reconstitute(id, org, plan, ExerciseKind.FULL_RECOVERY, Outcome.PARTIAL, start, end, 5, FailureReason.TIMEOUT, "tool", now);
        assertEquals(ExerciseKind.FULL_RECOVERY, back.getKind());
        assertEquals(FailureReason.TIMEOUT, back.getFailureReason());
        assertEquals(5, back.getDataLossMinutes());
        rejects(() -> RecoveryExercise.reconstitute(null, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "t", now));
        rejects(() -> RecoveryExercise.reconstitute(id, null, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "t", now));
        rejects(() -> RecoveryExercise.reconstitute(id, org, null, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end, null, null, "t", now));
        rejects(() -> RecoveryExercise.reconstitute(id, org, plan, null, Outcome.SUCCEEDED, start, end, null, null, "t", now));
        rejects(() -> RecoveryExercise.reconstitute(id, org, plan, ExerciseKind.FAILOVER, null, start, end, null, null, "t", now));
        rejects(() -> RecoveryExercise.reconstitute(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, null, end, null, null, "t", now));
        rejects(() -> RecoveryExercise.reconstitute(id, org, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, null, null, null, "t", now));
    }
}
