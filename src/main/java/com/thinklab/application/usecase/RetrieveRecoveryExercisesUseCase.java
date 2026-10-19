package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.RecoveryExerciseResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Filter;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's exercises, newest start first, filterable by plan, kind and outcome, at most {@value #MAX_LIMIT} (BIAN Behavior Qualifier: {@code exercise/retrieve}, collection). */
@Singleton
public class RetrieveRecoveryExercisesUseCase {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(RetrieveRecoveryExercisesUseCase.class);

    private final RecoveryExerciseRepository repository;

    public RetrieveRecoveryExercisesUseCase(RecoveryExerciseRepository repository) {
        this.repository = repository;
    }

    public Flux<RecoveryExerciseResponse> execute(UUID organisationId, Filter filter, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the RecoveryExercises of organisation: {}", organisationId);

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "list recovery exercises"))
                .then(Mono.fromCallable(() -> effectiveLimit(limit)))
                .flatMapMany(effective -> repository.findAll(organisationId, filter, effective))
                .map(DrMapper::toResponse);
    }

    private static int effectiveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("The limit must be between 1 and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
