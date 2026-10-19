package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** A plan and how its promise holds right now: {@code readiness}, {@code nextExerciseDueAt}, {@code rtoMet} and {@code rpoMet} are judged when read, never stored. */
@Serdeable
public record RecoveryPlanResponse(UUID id, UUID organisationId, String name, UUID assetId, String strategy, int rtoMinutes, int rpoMinutes, int testEveryDays,
                                   @Nullable String runbookRef, String status, String readiness, @Nullable Instant nextExerciseDueAt, @Nullable Boolean rtoMet,
                                   @Nullable Boolean rpoMet, @Nullable Instant lastExerciseAt, @Nullable String lastOutcome, @Nullable Instant lastPassedAt,
                                   @Nullable Integer lastPassedMinutes, @Nullable Integer lastPassedDataLossMinutes, Instant createdAt, Instant updatedAt) {}
