package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Readiness;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for listing the tenant's plans, oldest first, judged now, filterable by status, asset and readiness (BIAN Behavior Qualifier:
 * {@code retrieve}, collection). The readiness is judged when read, so it is filtered in memory over the tenant's plans (ADR-031).
 */
@Singleton
public class RetrieveRecoveryPlansUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveRecoveryPlansUseCase.class);

    private final RecoveryPlanRepository repository;
    private final Clock clock;

    public RetrieveRecoveryPlansUseCase(RecoveryPlanRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Flux<RecoveryPlanResponse> execute(UUID organisationId, PlanStatus status, UUID assetId, Readiness readiness, String role) {
        log.info("[USE CASE] Retrieving the RecoveryPlans of organisation: {}", organisationId);

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "list recovery plans"))
                .thenMany(Flux.defer(() -> {
                    Instant now = Instant.now(clock);
                    return repository.findAll(organisationId)
                            .filter(plan -> status == null || plan.getStatus() == status)
                            .filter(plan -> assetId == null || assetId.equals(plan.getAssetId()))
                            .filter(plan -> readiness == null || plan.assess(now).readiness() == readiness)
                            .map(plan -> DrMapper.toResponse(plan, now));
                }));
    }
}
