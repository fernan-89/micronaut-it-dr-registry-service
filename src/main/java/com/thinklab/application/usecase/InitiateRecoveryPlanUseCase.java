package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateRecoveryPlanRequest;
import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Use Case for promising how an asset will be recovered (BIAN Behavior Qualifier: {@code initiate}). Staff only. */
@Singleton
public class InitiateRecoveryPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateRecoveryPlanUseCase.class);

    private final HashServicePort hashServicePort;
    private final RecoveryPlanRepository repository;
    private final Clock clock;

    public InitiateRecoveryPlanUseCase(HashServicePort hashServicePort, RecoveryPlanRepository repository, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<RecoveryPlanResponse> execute(UUID organisationId, InitiateRecoveryPlanRequest request, String executor, String role) {
        log.info("[USE CASE] Creating the RecoveryPlan [{}] for organisation: {}", request.name(), organisationId);

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "create a recovery plan"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("recovery-plan-creation")))
                .map(id -> RecoveryPlan.createNew(id, organisationId, request.name(), request.assetId(), request.strategy(), request.rtoMinutes(),
                        request.rpoMinutes(), request.testEveryDays(), request.runbookRef(), executor))
                .flatMap(repository::create)
                .map(plan -> DrMapper.toResponse(plan, Instant.now(clock)));
    }
}
