package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.exception.RecoveryPlanNotFoundException;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Use Case for reading one plan of the tenant, judged now (BIAN Behavior Qualifier: {@code retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveRecoveryPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveRecoveryPlanUseCase.class);

    private final RecoveryPlanRepository repository;
    private final Clock clock;

    public RetrieveRecoveryPlanUseCase(RecoveryPlanRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<RecoveryPlanResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving RecoveryPlan ID: {}", id);

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "read a recovery plan"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new RecoveryPlanNotFoundException("RecoveryPlan " + id + " not found.")))
                .map(plan -> DrMapper.toResponse(plan, Instant.now(clock)));
    }
}
