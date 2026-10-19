package com.thinklab.domain.repository;

import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryExercise;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for RecoveryPlan persistence. {@link #save} is a guarded write on the status loaded (ADR-033); every lookup is tenant-scoped. */
public interface RecoveryPlanRepository {

    /** Inserts the plan. A name already used in the organisation is {@link com.thinklab.domain.exception.DuplicateRecoveryPlanException}: the unique index is the atomic backstop. */
    Mono<RecoveryPlan> create(RecoveryPlan plan);

    Mono<RecoveryPlan> findById(UUID id, UUID organisationId);

    /** The tenant's plans, oldest first. */
    Flux<RecoveryPlan> findAll(UUID organisationId);

    Mono<Void> save(RecoveryPlan plan, PlanStatus expectedStatus, PlanAuditEntry auditEntry);

    /**
     * Folds a reported exercise into the summary the plan keeps (ADR-030): the last exercise and what came of it, and the last one that passed with how
     * long the recovery took and how much data it lost. Both only move forward in time, so reports that arrive late or twice change nothing they should not.
     */
    Mono<Void> recordExercise(UUID planId, UUID organisationId, RecoveryExercise exercise);
}
