package com.thinklab.domain.repository;

import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound Port for RecoveryExercise persistence: exercises are facts, inserted once and never changed. Every lookup is tenant-scoped. */
public interface RecoveryExerciseRepository {

    /**
     * Inserts the exercise. The same plan, kind and start is the same exercise (a unique index): the loser of a repeated or simultaneous report gets the
     * stored one back and {@code created} false, so a tool may retry a report as often as it likes.
     */
    Mono<Stored> create(RecoveryExercise exercise);

    /** The exercise already reported for that plan, kind and start, if any. */
    Mono<RecoveryExercise> findByNaturalKey(UUID organisationId, UUID planId, ExerciseKind kind, Instant startedAt);

    Mono<RecoveryExercise> findById(UUID id, UUID organisationId);

    /** The tenant's exercises, newest start first, at most {@code limit}. */
    Flux<RecoveryExercise> findAll(UUID organisationId, Filter filter, int limit);

    record Stored(RecoveryExercise exercise, boolean created) {
    }

    record Filter(UUID planId, ExerciseKind kind, Outcome outcome) {
    }
}
