package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.UUID;

/** Infrastructure-specific representation of the RecoveryExercise Aggregate for MongoDB. Immutable facts: only ever inserted. */
@Introspected
public class RecoveryExerciseDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID planId;
    private String kind;
    private String outcome;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer dataLossMinutes;
    private String failureReason;
    private String reportedBy;
    private Instant reportedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Integer getDataLossMinutes() { return dataLossMinutes; }
    public void setDataLossMinutes(Integer dataLossMinutes) { this.dataLossMinutes = dataLossMinutes; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }

    public static final class RecoveryExercisePersistenceMapper {

        private RecoveryExercisePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static RecoveryExerciseDocument toDocument(RecoveryExercise exercise) {
            RecoveryExerciseDocument doc = new RecoveryExerciseDocument();
            doc.setId(exercise.getId());
            doc.setOrganisationId(exercise.getOrganisationId());
            doc.setPlanId(exercise.getPlanId());
            doc.setKind(exercise.getKind().name());
            doc.setOutcome(exercise.getOutcome().name());
            doc.setStartedAt(exercise.getStartedAt());
            doc.setFinishedAt(exercise.getFinishedAt());
            doc.setDataLossMinutes(exercise.getDataLossMinutes());
            doc.setFailureReason(exercise.getFailureReason() != null ? exercise.getFailureReason().name() : null);
            doc.setReportedBy(exercise.getReportedBy());
            doc.setReportedAt(exercise.getReportedAt());
            return doc;
        }

        public static RecoveryExercise toDomain(RecoveryExerciseDocument doc) {
            return RecoveryExercise.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getPlanId(), ExerciseKind.valueOf(doc.getKind()), Outcome.valueOf(doc.getOutcome()),
                    doc.getStartedAt(), doc.getFinishedAt(), doc.getDataLossMinutes(), doc.getFailureReason() != null ? FailureReason.valueOf(doc.getFailureReason()) : null,
                    doc.getReportedBy(), doc.getReportedAt());
        }
    }
}
