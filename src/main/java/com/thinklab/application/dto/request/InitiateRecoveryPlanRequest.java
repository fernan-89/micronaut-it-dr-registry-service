package com.thinklab.application.dto.request;

import com.thinklab.domain.model.RecoveryPlan.Strategy;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for {@code initiate}: the promise made about recovering one asset. A plan that restores from a backup cannot promise a recovery point of
 * zero (the domain refuses it with a 400 that says why); {@code runbookRef} is an identifier, never a sentence.
 */
@Serdeable
public record InitiateRecoveryPlanRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
        @NotNull(message = "The asset the plan recovers is required") UUID assetId,
        @NotNull(message = "The strategy is required") Strategy strategy,
        @NotNull(message = "The recovery time objective is required") @Min(value = 1, message = "RTO must be at least 1 minute") @Max(value = 525600, message = "RTO must not exceed 525600 minutes")
        Integer rtoMinutes,
        @NotNull(message = "The recovery point objective is required") @Min(value = 0, message = "RPO must not be negative") @Max(value = 525600, message = "RPO must not exceed 525600 minutes")
        Integer rpoMinutes,
        @NotNull(message = "The exercise interval is required") @Min(value = 1, message = "The exercise interval must be at least 1 day") @Max(value = 365, message = "The exercise interval must not exceed 365 days")
        Integer testEveryDays,
        @Nullable @Size(max = 64, message = "The runbook reference must not exceed 64 characters")
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/#-]*", message = "The runbook reference must be an identifier: letters, digits and . _ : / # -, no spaces")
        String runbookRef
) {}
