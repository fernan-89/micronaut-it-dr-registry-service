package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidRecoveryPlanStatusException;
import com.thinklab.domain.model.RecoveryExercise.Outcome;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aggregate Root (BIAN Control Record) of the IT DR Registry Service Domain: the promise made about recovering one asset from a disaster. How it
 * is meant to come back ({@link Strategy}), how long it may stay down ({@code rtoMinutes}, the recovery time objective), how much data it may
 * lose ({@code rpoMinutes}, the recovery point objective), how often the plan must be proved by an exercise, and optionally where the runbook
 * is (an identifier, never a sentence). The person or tool that runs an exercise reports it to the registry (ADR-030); the plan keeps only a
 * summary of them (the last exercise and its outcome, the last one that passed, and the last measured one with how long the recovery took) and judges itself when read (ADR-031):
 * nothing here runs, schedules or reaches anything.
 */
public class RecoveryPlan {

    public static final int MAX_MINUTES = 525600;
    public static final int MAX_TEST_DAYS = 365;
    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_RUNBOOK_REF_LENGTH = 64;
    /** An identifier (an article id, a document number, a path-like reference), never prose: no spaces, nothing a sentence is made of. */
    private static final Pattern RUNBOOK_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/#-]*");

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private UUID assetId;
    private Strategy strategy;
    private int rtoMinutes;
    private int rpoMinutes;
    private int testEveryDays;
    private String runbookRef;
    private PlanStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastExerciseAt;
    private Outcome lastOutcome;
    private Instant lastPassedAt;
    private Instant lastMeasuredAt;
    private Integer lastMeasuredMinutes;
    private Integer lastMeasuredDataLossMinutes;
    private final List<PlanAuditEntry> auditTrail;

    private RecoveryPlan(UUID id, UUID organisationId, String name, UUID assetId, Strategy strategy, int rtoMinutes, int rpoMinutes, int testEveryDays, String runbookRef,
                         String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.assetId = assetId;
        this.strategy = strategy;
        this.rtoMinutes = rtoMinutes;
        this.rpoMinutes = rpoMinutes;
        this.testEveryDays = testEveryDays;
        this.runbookRef = runbookRef;
        this.status = PlanStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new PlanAuditEntry(this.createdAt, "INITIATED", executor, null, PlanStatus.ACTIVE, "Recovery plan for asset " + assetId + "."));
    }

    private RecoveryPlan(UUID id, UUID organisationId, String name, UUID assetId, Strategy strategy, int rtoMinutes, int rpoMinutes, int testEveryDays, String runbookRef,
                         PlanStatus status, Instant createdAt, Instant updatedAt, Instant lastExerciseAt, Outcome lastOutcome, Instant lastPassedAt, Instant lastMeasuredAt,
                         Integer lastMeasuredMinutes, Integer lastMeasuredDataLossMinutes, List<PlanAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.assetId = assetId;
        this.strategy = strategy;
        this.rtoMinutes = rtoMinutes;
        this.rpoMinutes = rpoMinutes;
        this.testEveryDays = testEveryDays;
        this.runbookRef = runbookRef;
        this.status = status != null ? status : PlanStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.lastExerciseAt = lastExerciseAt;
        this.lastOutcome = lastOutcome;
        this.lastPassedAt = lastPassedAt;
        this.lastMeasuredAt = lastMeasuredAt;
        this.lastMeasuredMinutes = lastMeasuredMinutes;
        this.lastMeasuredDataLossMinutes = lastMeasuredDataLossMinutes;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static RecoveryPlan createNew(UUID id, UUID organisationId, String name, UUID assetId, Strategy strategy, int rtoMinutes, int rpoMinutes, int testEveryDays,
                                         String runbookRef, String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for RecoveryPlan creation.");
        }
        validate(name, assetId, strategy, rtoMinutes, rpoMinutes, testEveryDays, runbookRef);
        requireExecutor(executor);
        return new RecoveryPlan(id, organisationId, name, assetId, strategy, rtoMinutes, rpoMinutes, testEveryDays, runbookRef, executor);
    }

    public static RecoveryPlan reconstitute(UUID id, UUID organisationId, String name, UUID assetId, Strategy strategy, int rtoMinutes, int rpoMinutes, int testEveryDays,
                                            String runbookRef, PlanStatus status, Instant createdAt, Instant updatedAt, Instant lastExerciseAt, Outcome lastOutcome,
                                            Instant lastPassedAt, Instant lastMeasuredAt, Integer lastMeasuredMinutes, Integer lastMeasuredDataLossMinutes, List<PlanAuditEntry> auditTrail) {
        if (id == null || organisationId == null || name == null || assetId == null || strategy == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Name, Asset ID and Strategy are mandatory to reconstitute a RecoveryPlan.");
        }
        return new RecoveryPlan(id, organisationId, name, assetId, strategy, rtoMinutes, rpoMinutes, testEveryDays, runbookRef, status, createdAt, updatedAt, lastExerciseAt,
                lastOutcome, lastPassedAt, lastMeasuredAt, lastMeasuredMinutes, lastMeasuredDataLossMinutes, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. The whole definition again, in any status; the summary of the exercises is not touched. */
    public PlanAuditEntry update(String newName, UUID newAssetId, Strategy newStrategy, int newRtoMinutes, int newRpoMinutes, int newTestEveryDays, String newRunbookRef,
                                 String executor) {
        validate(newName, newAssetId, newStrategy, newRtoMinutes, newRpoMinutes, newTestEveryDays, newRunbookRef);
        requireExecutor(executor);
        this.name = newName;
        this.assetId = newAssetId;
        this.strategy = newStrategy;
        this.rtoMinutes = newRtoMinutes;
        this.rpoMinutes = newRpoMinutes;
        this.testEveryDays = newTestEveryDays;
        this.runbookRef = newRunbookRef;
        return record("UPDATED", executor, this.status, this.status, "Plan updated.");
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: judged by nobody until it is resumed (planned work, a retired source). */
    public PlanAuditEntry pause(String executor) {
        requireStatus(PlanStatus.ACTIVE);
        requireExecutor(executor);
        this.status = PlanStatus.PAUSED;
        return record("PAUSED", executor, PlanStatus.ACTIVE, PlanStatus.PAUSED, "Paused: the plan is no longer judged.");
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    public PlanAuditEntry resume(String executor) {
        requireStatus(PlanStatus.PAUSED);
        requireExecutor(executor);
        this.status = PlanStatus.ACTIVE;
        return record("RESUMED", executor, PlanStatus.PAUSED, PlanStatus.ACTIVE, "Resumed.");
    }

    /**
     * How well the promise holds at that moment (ADR-031), the first of these that applies: a paused plan is not judged; if the latest exercise was
     * partial or failed the plan is not proved ({@code LAST_EXERCISE_FAILED}); if the last measured pass took longer than the recovery time,
     * or lost more data than the recovery point allows, the objective is missed; an exercise is due {@code testEveryDays} after the last one that
     * passed, or after the plan was created, and a plan past that is {@code TEST_OVERDUE}; a plan never exercised is {@code NEVER_TESTED}; else
     * {@code READY}. A later passing exercise clears an earlier failure.
     */
    public Assessment assess(Instant now) {
        if (status == PlanStatus.PAUSED) {
            return new Assessment(Readiness.PAUSED, null, rtoMet(), rpoMet());
        }
        Instant due = (lastPassedAt != null ? lastPassedAt : createdAt).plus(Duration.ofDays(testEveryDays));
        Boolean rtoMet = rtoMet();
        Boolean rpoMet = rpoMet();
        Readiness readiness;
        if (lastOutcome != null && lastOutcome != Outcome.SUCCEEDED) {
            readiness = Readiness.LAST_EXERCISE_FAILED;
        } else if (Boolean.FALSE.equals(rtoMet) || Boolean.FALSE.equals(rpoMet)) {
            readiness = Readiness.OBJECTIVE_MISSED;
        } else if (now.isAfter(due)) {
            readiness = Readiness.TEST_OVERDUE;
        } else {
            readiness = lastPassedAt == null ? Readiness.NEVER_TESTED : Readiness.READY;
        }
        return new Assessment(readiness, due, rtoMet, rpoMet);
    }

    /** Did the last measured pass (a failover or a full recovery) fit in the recovery time objective? Unknown (null) until there is one. */
    private Boolean rtoMet() {
        return lastMeasuredMinutes == null ? null : lastMeasuredMinutes <= rtoMinutes;
    }

    /** Did the last measured pass lose no more than the recovery point allows? Unknown (null) until one reported what it lost. */
    private Boolean rpoMet() {
        return lastMeasuredDataLossMinutes == null ? null : lastMeasuredDataLossMinutes <= rpoMinutes;
    }

    // --- Internal helpers ---

    private PlanAuditEntry record(String action, String executor, PlanStatus from, PlanStatus to, String detail) {
        this.updatedAt = Instant.now();
        PlanAuditEntry entry = new PlanAuditEntry(this.updatedAt, action, executor, from, to, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(PlanStatus expected) {
        if (this.status != expected) {
            throw new InvalidRecoveryPlanStatusException(String.format("Illegal transition: RecoveryPlan is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable RecoveryPlan mutations.");
        }
    }

    private static void validate(String name, UUID assetId, Strategy strategy, int rtoMinutes, int rpoMinutes, int testEveryDays, String runbookRef) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name is mandatory for a RecoveryPlan (up to " + MAX_NAME_LENGTH + " characters).");
        }
        if (assetId == null) {
            throw new IllegalArgumentException("The asset the plan recovers is mandatory.");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("The strategy of the plan is mandatory.");
        }
        if (rtoMinutes < 1 || rtoMinutes > MAX_MINUTES) {
            throw new IllegalArgumentException("The recovery time objective must be between 1 and " + MAX_MINUTES + " minutes.");
        }
        if (rpoMinutes < 0 || rpoMinutes > MAX_MINUTES) {
            throw new IllegalArgumentException("The recovery point objective must be between 0 and " + MAX_MINUTES + " minutes.");
        }
        if (strategy == Strategy.BACKUP_RESTORE && rpoMinutes < 1) {
            throw new IllegalArgumentException("A plan that restores from a backup cannot promise to lose no data: use a standby strategy for a recovery point of zero.");
        }
        if (testEveryDays < 1 || testEveryDays > MAX_TEST_DAYS) {
            throw new IllegalArgumentException("The exercise interval must be between 1 and " + MAX_TEST_DAYS + " days.");
        }
        if (runbookRef != null && (runbookRef.length() > MAX_RUNBOOK_REF_LENGTH || !RUNBOOK_REF.matcher(runbookRef).matches())) {
            throw new IllegalArgumentException("The runbook reference must be an identifier (letters, digits and . _ : / # -, up to " + MAX_RUNBOOK_REF_LENGTH
                    + " characters, no spaces), or left out.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public UUID getAssetId() { return assetId; }
    public Strategy getStrategy() { return strategy; }
    public int getRtoMinutes() { return rtoMinutes; }
    public int getRpoMinutes() { return rpoMinutes; }
    public int getTestEveryDays() { return testEveryDays; }
    public String getRunbookRef() { return runbookRef; }
    public PlanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastExerciseAt() { return lastExerciseAt; }
    public Outcome getLastOutcome() { return lastOutcome; }
    public Instant getLastPassedAt() { return lastPassedAt; }
    public Instant getLastMeasuredAt() { return lastMeasuredAt; }
    public Integer getLastMeasuredMinutes() { return lastMeasuredMinutes; }
    public Integer getLastMeasuredDataLossMinutes() { return lastMeasuredDataLossMinutes; }
    public List<PlanAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum PlanStatus { ACTIVE, PAUSED }

    /** How the asset is meant to come back: from a backup, from a minimal copy kept running, from a scaled-down copy, or from a copy serving traffic already. */
    public enum Strategy { BACKUP_RESTORE, PILOT_LIGHT, WARM_STANDBY, ACTIVE_ACTIVE }

    /** The first that applies, in this order: PAUSED is not judged; LAST_EXERCISE_FAILED, OBJECTIVE_MISSED, TEST_OVERDUE; NEVER_TESTED is still inside its first interval; READY is proved. */
    public enum Readiness { PAUSED, LAST_EXERCISE_FAILED, OBJECTIVE_MISSED, TEST_OVERDUE, NEVER_TESTED, READY }

    public record PlanAuditEntry(Instant occurredAt, String action, String executor, PlanStatus fromStatus, PlanStatus toStatus, String detail) {}

    /** The judgment of one moment: nothing of it is stored. {@code nextExerciseDueAt} is null for a paused plan; {@code rtoMet} and {@code rpoMet} are null until a failover or a full recovery passed (and reported its data loss, for the second). */
    public record Assessment(Readiness readiness, Instant nextExerciseDueAt, Boolean rtoMet, Boolean rpoMet) {}
}
