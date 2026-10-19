package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidRecoveryPlanStatusException;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryPlan.Assessment;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Readiness;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryPlanTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();

    /** A plan with a 120 minute recovery time, a 30 minute recovery point and an exercise every 30 days. */
    private RecoveryPlan plan() {
        return RecoveryPlan.createNew(UUID.randomUUID(), org, "Orders failover", asset, Strategy.WARM_STANDBY, 120, 30, 30, "KB-1042", "op");
    }

    /** A plan created 100 days ago whose summary of the exercises is exactly what the test says. */
    private RecoveryPlan summarised(PlanStatus status, Outcome lastOutcome, Instant lastPassedAt, Integer passedMinutes, Integer passedDataLoss) {
        Instant created = NOW.minus(Duration.ofDays(100));
        Instant lastExercise = lastOutcome == null ? null : NOW.minus(Duration.ofDays(1));
        return RecoveryPlan.reconstitute(UUID.randomUUID(), org, "P", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, status, created, created, lastExercise, lastOutcome,
                lastPassedAt, passedMinutes, passedDataLoss, List.of());
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    // ------------------------------------------------------------------ Creation

    @Test
    @DisplayName("a new plan is ACTIVE, with one INITIATED entry, and the summary of the exercises is empty")
    void createNew() {
        RecoveryPlan plan = plan();

        assertEquals(PlanStatus.ACTIVE, plan.getStatus());
        assertEquals("Orders failover", plan.getName());
        assertEquals(asset, plan.getAssetId());
        assertEquals(org, plan.getOrganisationId());
        assertEquals(Strategy.WARM_STANDBY, plan.getStrategy());
        assertEquals(120, plan.getRtoMinutes());
        assertEquals(30, plan.getRpoMinutes());
        assertEquals(30, plan.getTestEveryDays());
        assertEquals("KB-1042", plan.getRunbookRef());
        assertNull(plan.getLastExerciseAt());
        assertNull(plan.getLastOutcome());
        assertNull(plan.getLastPassedAt());
        assertNull(plan.getLastPassedMinutes());
        assertNull(plan.getLastPassedDataLossMinutes());
        assertEquals(plan.getCreatedAt(), plan.getUpdatedAt());
        assertEquals(1, plan.getAuditTrail().size());
        assertEquals("INITIATED", plan.getAuditTrail().get(0).action());
        assertNull(plan.getAuditTrail().get(0).fromStatus());
        assertThrows(UnsupportedOperationException.class, () -> plan.getAuditTrail().clear());
    }

    @Test
    @DisplayName("creation refuses what makes no sense, and says why")
    void createRefuses() {
        UUID id = UUID.randomUUID();
        rejects(() -> RecoveryPlan.createNew(null, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, null, "N", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, null, asset, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, " ", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "x".repeat(81), asset, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", null, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, null, 120, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 0, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, RecoveryPlan.MAX_MINUTES + 1, 30, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, -1, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, RecoveryPlan.MAX_MINUTES + 1, 30, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, 0, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, RecoveryPlan.MAX_TEST_DAYS + 1, null, "op"));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, null));
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, " "));
    }

    @Test
    @DisplayName("a plan that restores from a backup cannot promise to lose nothing, a standby one can")
    void backupRestoreCannotPromiseNoLoss() {
        UUID id = UUID.randomUUID();
        rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.BACKUP_RESTORE, 120, 0, 30, null, "op"));
        assertEquals(1, RecoveryPlan.createNew(id, org, "N", asset, Strategy.BACKUP_RESTORE, 120, 1, 30, null, "op").getRpoMinutes());
        assertEquals(0, RecoveryPlan.createNew(id, org, "N", asset, Strategy.ACTIVE_ACTIVE, 5, 0, 30, null, "op").getRpoMinutes());
        assertEquals(0, RecoveryPlan.createNew(id, org, "N", asset, Strategy.PILOT_LIGHT, 5, 0, 30, null, "op").getRpoMinutes());
    }

    @Test
    @DisplayName("the largest and the smallest allowed numbers are accepted")
    void limits() {
        UUID id = UUID.randomUUID();
        RecoveryPlan top = RecoveryPlan.createNew(id, org, "x".repeat(RecoveryPlan.MAX_NAME_LENGTH), asset, Strategy.PILOT_LIGHT, RecoveryPlan.MAX_MINUTES, RecoveryPlan.MAX_MINUTES,
                RecoveryPlan.MAX_TEST_DAYS, "A".repeat(RecoveryPlan.MAX_RUNBOOK_REF_LENGTH), "op");
        assertEquals(RecoveryPlan.MAX_MINUTES, top.getRtoMinutes());
        RecoveryPlan bottom = RecoveryPlan.createNew(id, org, "N", asset, Strategy.PILOT_LIGHT, 1, 0, 1, null, "op");
        assertEquals(1, bottom.getTestEveryDays());
        assertNull(bottom.getRunbookRef());
    }

    @Test
    @DisplayName("the runbook reference is an identifier, never a sentence")
    void runbookReference() {
        UUID id = UUID.randomUUID();
        for (String good : new String[]{"KB-1042", "runbooks/dr/orders.md", "wiki:DR#3", "9f1c2e0a-0000-4000-8000-000000000001", "a"}) {
            assertEquals(good, RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, 30, good, "op").getRunbookRef());
        }
        for (String bad : new String[]{"", " ", "restart the database first", "-starts-with-dash", "has space", "semi;colon", "quote\"d", "A".repeat(RecoveryPlan.MAX_RUNBOOK_REF_LENGTH + 1)}) {
            rejects(() -> RecoveryPlan.createNew(id, org, "N", asset, Strategy.WARM_STANDBY, 120, 30, 30, bad, "op"));
        }
    }

    @Test
    @DisplayName("reconstitute needs the identity, the name, the asset and the strategy; a missing status, time or trail takes its default")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        RecoveryPlan bare = RecoveryPlan.reconstitute(id, org, "N", asset, Strategy.ACTIVE_ACTIVE, 5, 0, 7, null, null, null, null, null, null, null, null, null, null);
        assertEquals(PlanStatus.ACTIVE, bare.getStatus());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());
        assertEquals(0, bare.getAuditTrail().size());
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-02-01T00:00:00Z");
        RecoveryPlan full = RecoveryPlan.reconstitute(id, org, "N", asset, Strategy.PILOT_LIGHT, 60, 10, 14, "KB-1", PlanStatus.PAUSED, created, updated, NOW, Outcome.PARTIAL, NOW,
                45, 3, List.of(new PlanAuditEntry(created, "INITIATED", "op", null, PlanStatus.ACTIVE, "d")));
        assertEquals(PlanStatus.PAUSED, full.getStatus());
        assertEquals(created, full.getCreatedAt());
        assertEquals(updated, full.getUpdatedAt());
        assertEquals(NOW, full.getLastExerciseAt());
        assertEquals(Outcome.PARTIAL, full.getLastOutcome());
        assertEquals(NOW, full.getLastPassedAt());
        assertEquals(45, full.getLastPassedMinutes());
        assertEquals(3, full.getLastPassedDataLossMinutes());
        assertEquals(1, full.getAuditTrail().size());
        rejects(() -> RecoveryPlan.reconstitute(null, org, "N", asset, Strategy.PILOT_LIGHT, 1, 1, 1, null, null, null, null, null, null, null, null, null, null));
        rejects(() -> RecoveryPlan.reconstitute(id, null, "N", asset, Strategy.PILOT_LIGHT, 1, 1, 1, null, null, null, null, null, null, null, null, null, null));
        rejects(() -> RecoveryPlan.reconstitute(id, org, null, asset, Strategy.PILOT_LIGHT, 1, 1, 1, null, null, null, null, null, null, null, null, null, null));
        rejects(() -> RecoveryPlan.reconstitute(id, org, "N", null, Strategy.PILOT_LIGHT, 1, 1, 1, null, null, null, null, null, null, null, null, null, null));
        rejects(() -> RecoveryPlan.reconstitute(id, org, "N", asset, null, 1, 1, 1, null, null, null, null, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ Behaviors

    @Test
    @DisplayName("update replaces the whole definition in any status, records it, and leaves the summary of the exercises alone")
    void update() {
        RecoveryPlan plan = summarised(PlanStatus.PAUSED, Outcome.SUCCEEDED, NOW.minus(Duration.ofDays(2)), 50, 4);
        UUID otherAsset = UUID.randomUUID();

        PlanAuditEntry entry = plan.update("Renamed", otherAsset, Strategy.ACTIVE_ACTIVE, 10, 0, 7, null, "editor");

        assertEquals("UPDATED", entry.action());
        assertEquals("editor", entry.executor());
        assertEquals(PlanStatus.PAUSED, entry.fromStatus());
        assertEquals(PlanStatus.PAUSED, entry.toStatus());
        assertEquals("Renamed", plan.getName());
        assertEquals(otherAsset, plan.getAssetId());
        assertEquals(Strategy.ACTIVE_ACTIVE, plan.getStrategy());
        assertEquals(10, plan.getRtoMinutes());
        assertEquals(0, plan.getRpoMinutes());
        assertEquals(7, plan.getTestEveryDays());
        assertNull(plan.getRunbookRef());
        assertEquals(50, plan.getLastPassedMinutes());
        assertEquals(Outcome.SUCCEEDED, plan.getLastOutcome());
        assertEquals(PlanStatus.PAUSED, plan.getStatus());
        assertEquals(1, plan.getAuditTrail().size());
    }

    @Test
    @DisplayName("update refuses what creation refuses, and needs an executor; nothing changes")
    void updateRefuses() {
        RecoveryPlan plan = plan();
        rejects(() -> plan.update(" ", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, "op"));
        rejects(() -> plan.update("N", asset, Strategy.BACKUP_RESTORE, 120, 0, 30, null, "op"));
        rejects(() -> plan.update("N", asset, Strategy.WARM_STANDBY, 120, 30, 30, "two words", "op"));
        rejects(() -> plan.update("N", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, null));
        rejects(() -> plan.update("N", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, " "));
        assertEquals("Orders failover", plan.getName());
        assertEquals(1, plan.getAuditTrail().size());
    }

    @Test
    @DisplayName("pause and resume move between ACTIVE and PAUSED, each recorded; the wrong direction is an illegal transition")
    void pauseAndResume() {
        RecoveryPlan plan = plan();

        PlanAuditEntry paused = plan.pause("op");
        assertEquals(PlanStatus.PAUSED, plan.getStatus());
        assertEquals("PAUSED", paused.action());
        assertEquals(PlanStatus.ACTIVE, paused.fromStatus());
        assertThrows(InvalidRecoveryPlanStatusException.class, () -> plan.pause("op"));

        PlanAuditEntry resumed = plan.resume("op");
        assertEquals(PlanStatus.ACTIVE, plan.getStatus());
        assertEquals("RESUMED", resumed.action());
        assertThrows(InvalidRecoveryPlanStatusException.class, () -> plan.resume("op"));
        assertEquals(3, plan.getAuditTrail().size());
    }

    @Test
    @DisplayName("pause and resume need an executor")
    void controlNeedsAnExecutor() {
        RecoveryPlan active = plan();
        rejects(() -> active.pause(null));
        rejects(() -> active.pause(" "));
        RecoveryPlan paused = plan();
        paused.pause("op");
        rejects(() -> paused.resume(null));
        rejects(() -> paused.resume(" "));
        assertEquals(PlanStatus.PAUSED, paused.getStatus());
    }

    // ------------------------------------------------------------------ Judgment

    @Test
    @DisplayName("a paused plan is not judged, and says nothing about when the next exercise is due")
    void paused() {
        Assessment judged = summarised(PlanStatus.PAUSED, Outcome.FAILED, null, null, null).assess(NOW);

        assertEquals(Readiness.PAUSED, judged.readiness());
        assertNull(judged.nextExerciseDueAt());
        assertNull(judged.rtoMet());
        assertNull(judged.rpoMet());
        Assessment withPass = summarised(PlanStatus.PAUSED, Outcome.SUCCEEDED, NOW.minus(Duration.ofDays(3)), 60, 10).assess(NOW);
        assertEquals(Readiness.PAUSED, withPass.readiness());
        assertEquals(true, withPass.rtoMet());
        assertEquals(true, withPass.rpoMet());
    }

    @Test
    @DisplayName("a plan never exercised is NEVER_TESTED inside its first interval and TEST_OVERDUE after it")
    void neverExercised() {
        Instant created = NOW.minus(Duration.ofDays(10));
        RecoveryPlan young = RecoveryPlan.reconstitute(UUID.randomUUID(), org, "P", asset, Strategy.WARM_STANDBY, 120, 30, 30, null, PlanStatus.ACTIVE, created, created, null, null,
                null, null, null, List.of());
        Assessment first = young.assess(NOW);
        assertEquals(Readiness.NEVER_TESTED, first.readiness());
        assertEquals(created.plus(Duration.ofDays(30)), first.nextExerciseDueAt());
        assertNull(first.rtoMet());

        Assessment dueNow = young.assess(created.plus(Duration.ofDays(30)));
        assertEquals(Readiness.NEVER_TESTED, dueNow.readiness());
        assertEquals(Readiness.TEST_OVERDUE, young.assess(created.plus(Duration.ofDays(30)).plusMillis(1)).readiness());
        assertEquals(Readiness.TEST_OVERDUE, summarised(PlanStatus.ACTIVE, null, null, null, null).assess(NOW).readiness());
    }

    @Test
    @DisplayName("a pass inside the objectives is READY until the next exercise falls due, then TEST_OVERDUE")
    void ready() {
        Instant passed = NOW.minus(Duration.ofDays(5));
        Assessment judged = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 90, 20).assess(NOW);

        assertEquals(Readiness.READY, judged.readiness());
        assertEquals(passed.plus(Duration.ofDays(30)), judged.nextExerciseDueAt());
        assertEquals(true, judged.rtoMet());
        assertEquals(true, judged.rpoMet());

        RecoveryPlan plan = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 90, 20);
        assertEquals(Readiness.READY, plan.assess(passed.plus(Duration.ofDays(30))).readiness());
        assertEquals(Readiness.TEST_OVERDUE, plan.assess(passed.plus(Duration.ofDays(30)).plusMillis(1)).readiness());
    }

    @Test
    @DisplayName("a partial or failed latest exercise leaves the plan unproved, whatever passed before it")
    void lastExerciseFailed() {
        Instant passed = NOW.minus(Duration.ofDays(5));
        for (Outcome outcome : new Outcome[]{Outcome.PARTIAL, Outcome.FAILED}) {
            Assessment judged = summarised(PlanStatus.ACTIVE, outcome, passed, 90, 20).assess(NOW);
            assertEquals(Readiness.LAST_EXERCISE_FAILED, judged.readiness());
            assertEquals(passed.plus(Duration.ofDays(30)), judged.nextExerciseDueAt());
        }
        assertEquals(Readiness.LAST_EXERCISE_FAILED, summarised(PlanStatus.ACTIVE, Outcome.FAILED, null, null, null).assess(NOW).readiness());
        assertEquals(Readiness.LAST_EXERCISE_FAILED, summarised(PlanStatus.ACTIVE, Outcome.FAILED, passed, 500, 500).assess(NOW).readiness());
    }

    @Test
    @DisplayName("a pass that took longer than the recovery time, or lost more than the recovery point allows, misses the objective")
    void objectiveMissed() {
        Instant passed = NOW.minus(Duration.ofDays(5));

        Assessment slow = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 121, 5).assess(NOW);
        assertEquals(Readiness.OBJECTIVE_MISSED, slow.readiness());
        assertEquals(false, slow.rtoMet());
        assertEquals(true, slow.rpoMet());

        Assessment lossy = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 60, 31).assess(NOW);
        assertEquals(Readiness.OBJECTIVE_MISSED, lossy.readiness());
        assertEquals(true, lossy.rtoMet());
        assertEquals(false, lossy.rpoMet());

        Assessment both = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 500, 500).assess(NOW);
        assertEquals(Readiness.OBJECTIVE_MISSED, both.readiness());
    }

    @Test
    @DisplayName("exactly on the objective counts as met; an exercise that did not report its data loss says nothing about the recovery point")
    void objectiveEdges() {
        Instant passed = NOW.minus(Duration.ofDays(5));

        Assessment exact = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 120, 30).assess(NOW);
        assertEquals(Readiness.READY, exact.readiness());
        assertEquals(true, exact.rtoMet());
        assertEquals(true, exact.rpoMet());

        Assessment silent = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, passed, 60, null).assess(NOW);
        assertEquals(Readiness.READY, silent.readiness());
        assertEquals(true, silent.rtoMet());
        assertNull(silent.rpoMet());
    }

    @Test
    @DisplayName("a missed objective is a known defect and comes before an exercise that is only overdue")
    void missedBeforeOverdue() {
        Assessment judged = summarised(PlanStatus.ACTIVE, Outcome.SUCCEEDED, NOW.minus(Duration.ofDays(60)), 500, null).assess(NOW);
        assertEquals(Readiness.OBJECTIVE_MISSED, judged.readiness());
    }
}
