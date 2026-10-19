package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateRecoveryPlanRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing a plan (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateRecoveryPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateRecoveryPlanUseCase.class);

    private final RecoveryPlanWorkflow workflow;

    public UpdateRecoveryPlanUseCase(RecoveryPlanWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateRecoveryPlanRequest request, String executor, String role) {
        log.info("[USE CASE] Updating RecoveryPlan ID: {}", id);

        return workflow.apply(id, organisationId, role, "change a recovery plan",
                plan -> plan.update(request.name(), request.assetId(), request.strategy(), request.rtoMinutes(), request.rpoMinutes(), request.testEveryDays(),
                        request.runbookRef(), executor));
    }
}
