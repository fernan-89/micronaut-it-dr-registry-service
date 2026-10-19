package com.thinklab.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Secondary aggregate of the IT DR Registry Service Domain: one exercise of a recovery plan, as the person or tool that ran it reported it
 * (ADR-030). It is a fact, so it is immutable: never edited, never deleted. A partial or failed exercise carries one reason from a fixed
 * vocabulary, never text from the reporter (which could hold hostnames or credentials); a successful one carries none. Reporting the same
 * exercise twice (the same plan, kind and start) is the same exercise.
 */
public class RecoveryExercise {

    /** How far ahead of the registry's clock an exercise may claim to have finished: the reporter's clock is allowed to be a little off. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    public static final int MAX_DATA_LOSS_MINUTES = 525600;

    private final UUID id;
    private final UUID organisationId;
    private final UUID planId;
    private final ExerciseKind kind;
    private final Outcome outcome;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final Integer dataLossMinutes;
    private final FailureReason failureReason;
    private final String reportedBy;
    private final Instant reportedAt;

    private RecoveryExercise(UUID id, UUID organisationId, UUID planId, ExerciseKind kind, Outcome outcome, Instant startedAt, Instant finishedAt, Integer dataLossMinutes,
                             FailureReason failureReason, String reportedBy, Instant reportedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.planId = planId;
        this.kind = kind;
        this.outcome = outcome;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.dataLossMinutes = dataLossMinutes;
        this.failureReason = failureReason;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
    }

    public static RecoveryExercise createNew(UUID id, UUID organisationId, UUID planId, ExerciseKind kind, Outcome outcome, Instant startedAt, Instant finishedAt,
                                             Integer dataLossMinutes, FailureReason failureReason, String executor, Instant now) {
        if (id == null || organisationId == null || planId == null) {
            throw new IllegalArgumentException("ID, Organisation ID and Plan ID are mandatory for a RecoveryExercise.");
        }
        if (kind == null || outcome == null) {
            throw new IllegalArgumentException("The kind and the outcome of the exercise are mandatory.");
        }
        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("When the exercise started and when it finished are mandatory.");
        }
        startedAt = truncate(startedAt);
        finishedAt = truncate(finishedAt);
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("An exercise cannot finish before it started.");
        }
        if (finishedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw new IllegalArgumentException("An exercise cannot finish in the future.");
        }
        if (outcome != Outcome.SUCCEEDED && failureReason == null) {
            throw new IllegalArgumentException("A partial or failed exercise needs a reason.");
        }
        if (outcome == Outcome.SUCCEEDED && failureReason != null) {
            throw new IllegalArgumentException("A successful exercise has no failure reason.");
        }
        if (dataLossMinutes != null && (dataLossMinutes < 0 || dataLossMinutes > MAX_DATA_LOSS_MINUTES)) {
            throw new IllegalArgumentException("The data lost must be between 0 and " + MAX_DATA_LOSS_MINUTES + " minutes, or left out.");
        }
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory to report a RecoveryExercise.");
        }
        return new RecoveryExercise(id, organisationId, planId, kind, outcome, startedAt, finishedAt, dataLossMinutes, failureReason, executor, now);
    }

    public static RecoveryExercise reconstitute(UUID id, UUID organisationId, UUID planId, ExerciseKind kind, Outcome outcome, Instant startedAt, Instant finishedAt,
                                                Integer dataLossMinutes, FailureReason failureReason, String reportedBy, Instant reportedAt) {
        if (id == null || organisationId == null || planId == null || kind == null || outcome == null || startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Plan ID, kind, outcome and times are mandatory to reconstitute a RecoveryExercise.");
        }
        return new RecoveryExercise(id, organisationId, planId, kind, outcome, startedAt, finishedAt, dataLossMinutes, failureReason, reportedBy, reportedAt);
    }

    /** The store keeps milliseconds, and the start is part of the exercise's identity: every time is cut to the millisecond before it is compared or looked up. */
    public static Instant truncate(Instant moment) {
        return moment.truncatedTo(ChronoUnit.MILLIS);
    }

    /** How long the recovery took, in whole minutes rounded up and never less than one: what a passed exercise is judged against the recovery time objective with. */
    public int durationMinutes() {
        long seconds = Duration.between(startedAt, finishedAt).getSeconds();
        return (int) Math.max(1, (seconds + 59) / 60);
    }

    public boolean succeeded() {
        return outcome == Outcome.SUCCEEDED;
    }

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getPlanId() { return planId; }
    public ExerciseKind getKind() { return kind; }
    public Outcome getOutcome() { return outcome; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Integer getDataLossMinutes() { return dataLossMinutes; }
    public FailureReason getFailureReason() { return failureReason; }
    public String getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }

    /** A walk through the runbook on paper, a real failover to the standby, or a full recovery from nothing. */
    public enum ExerciseKind { TABLETOP, FAILOVER, FULL_RECOVERY }

    public enum Outcome { SUCCEEDED, PARTIAL, FAILED }

    /** The fixed vocabulary of what went wrong: never the reporter's own words. */
    public enum FailureReason { RUNBOOK_OUTDATED, DEPENDENCY_MISSING, ACCESS_PROBLEM, DATA_LOSS_EXCEEDED, TIMEOUT, OTHER }
}
