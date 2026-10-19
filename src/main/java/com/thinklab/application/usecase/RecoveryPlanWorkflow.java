package com.thinklab.application.usecase;

import com.thinklab.domain.exception.RecoveryPlanNotFoundException;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** The shape every staff change to a plan shares: staff only, load (a 404 if it is not the tenant's), apply, save with the status loaded. */
@Singleton
public class RecoveryPlanWorkflow {

    private final RecoveryPlanRepository repository;

    public RecoveryPlanWorkflow(RecoveryPlanRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<RecoveryPlan, PlanAuditEntry> action) {
        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, operation))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new RecoveryPlanNotFoundException("RecoveryPlan " + id + " not found.")))
                .flatMap(plan -> {
                    var statusBefore = plan.getStatus();
                    PlanAuditEntry entry = action.apply(plan);
                    return repository.save(plan, statusBefore, entry);
                });
    }
}
