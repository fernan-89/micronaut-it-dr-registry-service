package com.thinklab.application.dto.request;

import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for {@code exercise/initiate}: what the person or tool reports when an exercise of a plan ends. A partial or failed exercise names one reason
 * from a fixed list (never the reporter's own text); a successful one names none. {@code dataLossMinutes} is how much data was lost, when it is known.
 * Reporting the same plan, kind and start again answers the exercise already stored.
 */
@Serdeable
public record ReportRecoveryExerciseRequest(
        @NotNull(message = "The plan is required") UUID planId,
        @NotNull(message = "The kind of exercise is required") ExerciseKind kind,
        @NotNull(message = "The outcome is required") Outcome outcome,
        @NotNull(message = "When the exercise started is required") Instant startedAt,
        @NotNull(message = "When the exercise finished is required") Instant finishedAt,
        @Nullable @Min(value = 0, message = "The data lost cannot be negative") @Max(value = 525600, message = "The data lost must not exceed 525600 minutes") Integer dataLossMinutes,
        @Nullable FailureReason failureReason
) {}
