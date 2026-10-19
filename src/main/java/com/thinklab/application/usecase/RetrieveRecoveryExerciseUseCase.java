package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.RecoveryExerciseResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.exception.RecoveryExerciseNotFoundException;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one exercise of the tenant (BIAN Behavior Qualifier: {@code exercise/retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveRecoveryExerciseUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveRecoveryExerciseUseCase.class);

    private final RecoveryExerciseRepository repository;

    public RetrieveRecoveryExerciseUseCase(RecoveryExerciseRepository repository) {
        this.repository = repository;
    }

    public Mono<RecoveryExerciseResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving RecoveryExercise ID: {}", id);

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "read a recovery exercise"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new RecoveryExerciseNotFoundException("RecoveryExercise " + id + " not found.")))
                .map(DrMapper::toResponse);
    }
}
