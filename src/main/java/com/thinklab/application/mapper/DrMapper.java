package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.application.dto.response.RecoveryExerciseResponse;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.Assessment;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import com.thinklab.domain.model.RecoveryExercise;

import java.time.Instant;

public final class DrMapper {

    private DrMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** The plan as it stands, judged at {@code now}. */
    public static RecoveryPlanResponse toResponse(RecoveryPlan plan, Instant now) {
        Assessment judged = plan.assess(now);
        return new RecoveryPlanResponse(plan.getId(), plan.getOrganisationId(), plan.getName(), plan.getAssetId(), plan.getStrategy().name(), plan.getRtoMinutes(),
                plan.getRpoMinutes(), plan.getTestEveryDays(), plan.getRunbookRef(), plan.getStatus().name(), judged.readiness().name(), judged.nextExerciseDueAt(),
                judged.rtoMet(), judged.rpoMet(), plan.getLastExerciseAt(), plan.getLastOutcome() != null ? plan.getLastOutcome().name() : null, plan.getLastPassedAt(),
                plan.getLastPassedMinutes(), plan.getLastPassedDataLossMinutes(), plan.getCreatedAt(), plan.getUpdatedAt());
    }

    public static RecoveryExerciseResponse toResponse(RecoveryExercise exercise) {
        return new RecoveryExerciseResponse(exercise.getId(), exercise.getOrganisationId(), exercise.getPlanId(), exercise.getKind().name(), exercise.getOutcome().name(), exercise.getStartedAt(), exercise.getFinishedAt(),
                exercise.durationMinutes(), exercise.getDataLossMinutes(), exercise.getFailureReason() != null ? exercise.getFailureReason().name() : null, exercise.getReportedBy(), exercise.getReportedAt());
    }

    public static AuditEntryResponse toResponse(PlanAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(), entry.detail());
    }
}
