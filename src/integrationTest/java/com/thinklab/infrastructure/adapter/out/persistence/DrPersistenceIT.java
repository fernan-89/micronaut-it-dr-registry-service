package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateRecoveryPlanException;
import com.thinklab.domain.exception.InvalidRecoveryPlanStatusException;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Filter;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DR registry persistence against a real MongoDB: the unique plan name, the unique exercise key that makes a repeated or simultaneous
 * report the same exercise, the summary that only moves forward in time whatever the order of the reports, the status-guarded save, and the
 * indexes created at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "dr_registry_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject RecoveryPlanRepository plans;
    @Inject RecoveryExerciseRepository exercises;
    @Inject MongoClient mongoClient;

    private RecoveryPlan newPlan(UUID organisation, String name) {
        return RecoveryPlan.createNew(UUID.randomUUID(), organisation, name, UUID.randomUUID(), Strategy.WARM_STANDBY, 120, 30, 7, "KB-1", "op-1");
    }

    private RecoveryExercise newExercise(UUID organisation, UUID plan, ExerciseKind kind, Outcome outcome, Instant start, Instant end) {
        return RecoveryExercise.createNew(UUID.randomUUID(), organisation, plan, kind, outcome, start, end, null, outcome != Outcome.SUCCEEDED ? FailureReason.TIMEOUT : null, "tool", end);
    }

    @Test
    @DisplayName("a plan is read back whole, only for its own organisation, oldest first; the name is unique per organisation")
    void planRoundTrip() {
        UUID organisation = UUID.randomUUID();
        RecoveryPlan first = plans.create(newPlan(organisation, "First")).block();
        plans.create(newPlan(organisation, "Second")).block();

        RecoveryPlan found = plans.findById(first.getId(), organisation).block();

        assertEquals("First", found.getName());
        assertEquals(7, found.getTestEveryDays());
        assertEquals("KB-1", found.getRunbookRef());
        assertEquals(Strategy.WARM_STANDBY, found.getStrategy());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(plans.findById(first.getId(), UUID.randomUUID()).block());
        assertEquals(List.of("First", "Second"), plans.findAll(organisation).map(RecoveryPlan::getName).collectList().block());
        assertThrows(DuplicateRecoveryPlanException.class, () -> plans.create(newPlan(organisation, "First")).block());
        assertEquals("First", plans.create(newPlan(UUID.randomUUID(), "First")).block().getName());
    }

    @Test
    @DisplayName("a change is guarded by the status loaded: the second writer from the same state loses, and the audit entry is kept")
    void guardedSave() {
        UUID organisation = UUID.randomUUID();
        RecoveryPlan created = plans.create(newPlan(organisation, "Guarded")).block();
        RecoveryPlan writerOne = plans.findById(created.getId(), organisation).block();
        RecoveryPlan writerTwo = plans.findById(created.getId(), organisation).block();

        var entry = writerOne.pause("op-1");
        plans.save(writerOne, PlanStatus.ACTIVE, entry).block();
        var other = writerTwo.pause("op-2");

        assertThrows(InvalidRecoveryPlanStatusException.class, () -> plans.save(writerTwo, PlanStatus.ACTIVE, other).block());
        RecoveryPlan stored = plans.findById(created.getId(), organisation).block();
        assertEquals(PlanStatus.PAUSED, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size());
    }

    @Test
    @DisplayName("a rename to a name already in use is a duplicate")
    void renameDuplicate() {
        UUID organisation = UUID.randomUUID();
        plans.create(newPlan(organisation, "Taken")).block();
        RecoveryPlan other = plans.create(newPlan(organisation, "Free")).block();
        var entry = other.update("Taken", other.getAssetId(), Strategy.WARM_STANDBY, 120, 30, 7, null, "op-1");

        assertThrows(DuplicateRecoveryPlanException.class, () -> plans.save(other, PlanStatus.ACTIVE, entry).block());
    }

    @Test
    @DisplayName("the same exercise reported twice, even at once, is stored once and the others get the stored one back")
    void sameExercise() {
        UUID organisation = UUID.randomUUID();
        UUID plan = plans.create(newPlan(organisation, "Exercises")).block().getId();
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = start.plusSeconds(600);

        List<RecoveryExerciseRepository.Stored> results = Flux.range(0, 20).flatMap(i -> exercises.create(newExercise(organisation, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, start, end))).collectList().block();

        assertEquals(1, results.stream().filter(RecoveryExerciseRepository.Stored::created).count());
        assertEquals(1, results.stream().map(stored -> stored.exercise().getId()).distinct().count());
        assertEquals(1, exercises.findAll(organisation, new Filter(plan, null, null), 50).count().block());
        assertTrue(exercises.findByNaturalKey(organisation, plan, ExerciseKind.FAILOVER, RecoveryExercise.truncate(start)).block() != null);
        assertNull(exercises.findByNaturalKey(organisation, plan, ExerciseKind.FULL_RECOVERY, RecoveryExercise.truncate(start)).block());
    }

    @Test
    @DisplayName("exercises are listed newest start first, filtered, tenant-scoped and limited")
    void listing() {
        UUID organisation = UUID.randomUUID();
        UUID plan = plans.create(newPlan(organisation, "Listing")).block().getId();
        Instant base = Instant.now().minusSeconds(86400);
        RecoveryExercise old = exercises.create(newExercise(organisation, plan, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, base, base.plusSeconds(60))).block().exercise();
        RecoveryExercise failed = exercises.create(newExercise(organisation, plan, ExerciseKind.FAILOVER, Outcome.FAILED, base.plusSeconds(3600), base.plusSeconds(3660))).block().exercise();
        RecoveryExercise restore = exercises.create(newExercise(organisation, plan, ExerciseKind.FULL_RECOVERY, Outcome.SUCCEEDED, base.plusSeconds(7200), base.plusSeconds(7260))).block().exercise();

        assertEquals(List.of(restore.getId(), failed.getId(), old.getId()), exercises.findAll(organisation, new Filter(null, null, null), 50).map(RecoveryExercise::getId).collectList().block());
        assertEquals(List.of(failed.getId()), exercises.findAll(organisation, new Filter(plan, ExerciseKind.FAILOVER, Outcome.FAILED), 50).map(RecoveryExercise::getId).collectList().block());
        assertEquals(2, exercises.findAll(organisation, new Filter(null, null, null), 2).count().block());
        assertEquals(0, exercises.findAll(UUID.randomUUID(), new Filter(null, null, null), 50).count().block());
        assertEquals(FailureReason.TIMEOUT, exercises.findById(failed.getId(), organisation).block().getFailureReason());
        assertNull(exercises.findById(failed.getId(), UUID.randomUUID()).block());
    }

    @Test
    @DisplayName("the summary only moves forward: a late report changes nothing, a failure moves the last exercise but not the last pass")
    void summaryMovesForward() {
        UUID organisation = UUID.randomUUID();
        RecoveryPlan plan = plans.create(newPlan(organisation, "Summary")).block();
        Instant t1 = Instant.now().minusSeconds(7200);
        Instant t2 = t1.plusSeconds(3600);
        RecoveryExercise newer = newExercise(organisation, plan.getId(), ExerciseKind.FAILOVER, Outcome.SUCCEEDED, t2.minusSeconds(600), t2);
        RecoveryExercise older = newExercise(organisation, plan.getId(), ExerciseKind.FAILOVER, Outcome.SUCCEEDED, t1.minusSeconds(60), t1);
        RecoveryExercise failedLater = newExercise(organisation, plan.getId(), ExerciseKind.TABLETOP, Outcome.FAILED, t2.plusSeconds(60), t2.plusSeconds(120));

        plans.recordExercise(plan.getId(), organisation, newer).block();
        plans.recordExercise(plan.getId(), organisation, older).block();

        RecoveryPlan afterPasses = plans.findById(plan.getId(), organisation).block();
        assertEquals(RecoveryExercise.truncate(t2), afterPasses.getLastPassedAt());
        assertEquals(RecoveryExercise.truncate(t2), afterPasses.getLastExerciseAt());
        assertEquals(Outcome.SUCCEEDED, afterPasses.getLastOutcome());
        assertEquals(10, afterPasses.getLastMeasuredMinutes());
        assertEquals(RecoveryExercise.truncate(t2), afterPasses.getLastMeasuredAt());

        plans.recordExercise(plan.getId(), organisation, failedLater).block();
        plans.recordExercise(plan.getId(), organisation, older).block();

        RecoveryPlan after = plans.findById(plan.getId(), organisation).block();
        assertEquals(RecoveryExercise.truncate(t2.plusSeconds(120)), after.getLastExerciseAt());
        assertEquals(Outcome.FAILED, after.getLastOutcome());
        assertEquals(RecoveryExercise.truncate(t2), after.getLastPassedAt());
        assertEquals(10, after.getLastMeasuredMinutes());
        assertFalse(after.getLastPassedAt().isAfter(after.getLastExerciseAt()));
    }

    @Test
    @DisplayName("startup created the unique plan name and the unique exercise key")
    void indexesExist() {
        List<Document> planIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("recovery_plans").listIndexes()).collectList().block();
        List<Document> runIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("recovery_exercises").listIndexes()).collectList().block();

        assertTrue(planIndexes.stream().anyMatch(index -> "organisationId_1_name_1".equals(index.getString("name")) && Boolean.TRUE.equals(index.getBoolean("unique"))));
        assertTrue(runIndexes.stream().anyMatch(index -> "organisationId_1_planId_1_kind_1_startedAt_1".equals(index.getString("name")) && Boolean.TRUE.equals(index.getBoolean("unique"))));
        assertTrue(runIndexes.stream().anyMatch(index -> "organisationId_1_planId_1_startedAt_-1".equals(index.getString("name"))));
    }
}
