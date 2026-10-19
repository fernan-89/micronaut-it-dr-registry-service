package com.thinklab.application.usecase;

import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for pausing and resuming a plan (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlRecoveryPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlRecoveryPlanUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        PAUSE {
            @Override PlanAuditEntry apply(RecoveryPlan plan, String executor) { return plan.pause(executor); }
        },
        RESUME {
            @Override PlanAuditEntry apply(RecoveryPlan plan, String executor) { return plan.resume(executor); }
        };

        abstract PlanAuditEntry apply(RecoveryPlan plan, String executor);
    }

    private final RecoveryPlanWorkflow workflow;

    public ControlRecoveryPlanUseCase(RecoveryPlanWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on RecoveryPlan ID: {}", action, id);

        return workflow.apply(id, organisationId, role, "pause or resume a recovery plan", plan -> action.apply(plan, executor));
    }
}
