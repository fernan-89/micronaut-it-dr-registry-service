package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.model.Promise;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use Case for the promises of the tenant's recovery plans, judged now (BIAN Behavior Qualifier: {@code promises/retrieve}, platform ADR-001): a plan whose
 * latest exercise failed, whose last measured pass missed an objective or whose next exercise is overdue is broken (a fixed code each), a ready one is kept,
 * one never exercised yet or paused is not judged. Staff only.
 */
@Singleton
public class RetrievePromisesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrievePromisesUseCase.class);

    private final RecoveryPlanRepository repository;
    private final Clock clock;

    public RetrievePromisesUseCase(RecoveryPlanRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<List<PromiseResponse>> execute(UUID organisationId, Integer changedWithinDays, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the promises of the RecoveryPlans of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "list the promises of recovery plans"))
                .then(Mono.fromCallable(() -> new int[]{PromiseSelection.days(changedWithinDays), PromiseSelection.limit(limit)}))
                .flatMap(window -> {
                    Instant now = Instant.now(clock);
                    return repository.findAll(organisationId).map(plan -> toResponse(plan, now)).collectList()
                            .map(all -> PromiseSelection.select(all, now.minus(Duration.ofDays(window[0])), window[1]));
                });
    }

    private static PromiseResponse toResponse(RecoveryPlan plan, Instant now) {
        Promise promise = plan.promise(now);
        return new PromiseResponse(plan.getId(), plan.getName(), plan.getAssetId(), promise.verdict().name(), promise.code(), plan.getStatus().name(), plan.changedAt());
    }
}
