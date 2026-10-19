package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ReportRecoveryExerciseRequest;
import com.thinklab.application.dto.response.RecoveryExerciseResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.exception.RecoveryPlanNotFoundException;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Stored;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Use Case for what is reported when an exercise of a recovery plan ends (BIAN Behavior Qualifier: {@code exercise/initiate}), ADR-030.
 * Idempotent: the same plan, kind and start is the same exercise, so a tool may retry; the stored exercise comes back with {@code created} false.
 * Either way the plan's summary is folded again (it only moves forward in time), so a report that stored the exercise and died before the
 * summary was updated is repaired by the retry. A sovereign id is spent only for an exercise that is new.
 */
@Singleton
public class ReportRecoveryExerciseUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReportRecoveryExerciseUseCase.class);

    /** The exercise as stored, and whether this report created it (201) or found it already there (200). */
    public record Reported(RecoveryExerciseResponse exercise, boolean created) {
    }

    private final HashServicePort hashServicePort;
    private final RecoveryPlanRepository plans;
    private final RecoveryExerciseRepository exercises;
    private final Clock clock;

    public ReportRecoveryExerciseUseCase(HashServicePort hashServicePort, RecoveryPlanRepository plans, RecoveryExerciseRepository exercises, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.plans = plans;
        this.exercises = exercises;
        this.clock = clock;
    }

    public Mono<Reported> execute(UUID organisationId, ReportRecoveryExerciseRequest request, String executor, String role) {
        log.info("[USE CASE] Reporting a {} {} for RecoveryPlan ID: {}", request.kind(), request.outcome(), request.planId());

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "report a recovery exercise"))
                .then(Mono.defer(() -> plans.findById(request.planId(), organisationId)))
                .switchIfEmpty(Mono.error(new RecoveryPlanNotFoundException("RecoveryPlan " + request.planId() + " not found.")))
                .flatMap(plan -> exercises.findByNaturalKey(organisationId, plan.getId(), request.kind(), RecoveryExercise.truncate(request.startedAt()))
                        .map(existing -> new Stored(existing, false))
                        .switchIfEmpty(Mono.defer(() -> store(organisationId, request, executor))))
                .flatMap(stored -> plans.recordExercise(request.planId(), organisationId, stored.exercise())
                        .thenReturn(new Reported(DrMapper.toResponse(stored.exercise()), stored.created())));
    }

    private Mono<Stored> store(UUID organisationId, ReportRecoveryExerciseRequest request, String executor) {
        return hashServicePort.generateSovereignId("recovery-exercise-creation")
                .map(id -> RecoveryExercise.createNew(id, organisationId, request.planId(), request.kind(), request.outcome(), request.startedAt(), request.finishedAt(),
                        request.dataLossMinutes(), request.failureReason(), executor, Instant.now(clock)))
                .flatMap(exercises::create);
    }
}
