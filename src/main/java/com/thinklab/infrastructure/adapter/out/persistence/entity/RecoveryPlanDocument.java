package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the RecoveryPlan Aggregate for MongoDB. */
@Introspected
public class RecoveryPlanDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private String name;
    private UUID assetId;
    private String strategy;
    private int rtoMinutes;
    private int rpoMinutes;
    private int testEveryDays;
    private String runbookRef;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastExerciseAt;
    private String lastOutcome;
    private Instant lastPassedAt;
    private Integer lastPassedMinutes;
    private Integer lastPassedDataLossMinutes;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public int getRtoMinutes() { return rtoMinutes; }
    public void setRtoMinutes(int rtoMinutes) { this.rtoMinutes = rtoMinutes; }
    public int getRpoMinutes() { return rpoMinutes; }
    public void setRpoMinutes(int rpoMinutes) { this.rpoMinutes = rpoMinutes; }
    public int getTestEveryDays() { return testEveryDays; }
    public void setTestEveryDays(int testEveryDays) { this.testEveryDays = testEveryDays; }
    public String getRunbookRef() { return runbookRef; }
    public void setRunbookRef(String runbookRef) { this.runbookRef = runbookRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastExerciseAt() { return lastExerciseAt; }
    public void setLastExerciseAt(Instant lastExerciseAt) { this.lastExerciseAt = lastExerciseAt; }
    public String getLastOutcome() { return lastOutcome; }
    public void setLastOutcome(String lastOutcome) { this.lastOutcome = lastOutcome; }
    public Instant getLastPassedAt() { return lastPassedAt; }
    public void setLastPassedAt(Instant lastPassedAt) { this.lastPassedAt = lastPassedAt; }
    public Integer getLastPassedMinutes() { return lastPassedMinutes; }
    public void setLastPassedMinutes(Integer lastPassedMinutes) { this.lastPassedMinutes = lastPassedMinutes; }
    public Integer getLastPassedDataLossMinutes() { return lastPassedDataLossMinutes; }
    public void setLastPassedDataLossMinutes(Integer lastPassedDataLossMinutes) { this.lastPassedDataLossMinutes = lastPassedDataLossMinutes; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(PlanAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                    entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        PlanAuditEntry toDomain() {
            return new PlanAuditEntry(occurredAt, action, executor, fromStatus != null ? PlanStatus.valueOf(fromStatus) : null, PlanStatus.valueOf(toStatus), detail);
        }
    }

    public static final class RecoveryPlanPersistenceMapper {

        private RecoveryPlanPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static RecoveryPlanDocument toDocument(RecoveryPlan plan) {
            RecoveryPlanDocument doc = new RecoveryPlanDocument();
            doc.setId(plan.getId());
            doc.setOrganisationId(plan.getOrganisationId());
            doc.setName(plan.getName());
            doc.setAssetId(plan.getAssetId());
            doc.setStrategy(plan.getStrategy().name());
            doc.setRtoMinutes(plan.getRtoMinutes());
            doc.setRpoMinutes(plan.getRpoMinutes());
            doc.setTestEveryDays(plan.getTestEveryDays());
            doc.setRunbookRef(plan.getRunbookRef());
            doc.setStatus(plan.getStatus().name());
            doc.setCreatedAt(plan.getCreatedAt());
            doc.setUpdatedAt(plan.getUpdatedAt());
            doc.setLastExerciseAt(plan.getLastExerciseAt());
            doc.setLastOutcome(plan.getLastOutcome() != null ? plan.getLastOutcome().name() : null);
            doc.setLastPassedAt(plan.getLastPassedAt());
            doc.setLastPassedMinutes(plan.getLastPassedMinutes());
            doc.setLastPassedDataLossMinutes(plan.getLastPassedDataLossMinutes());
            doc.setAuditTrail(plan.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static RecoveryPlan toDomain(RecoveryPlanDocument doc) {
            return RecoveryPlan.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getName(), doc.getAssetId(), Strategy.valueOf(doc.getStrategy()), doc.getRtoMinutes(),
                    doc.getRpoMinutes(), doc.getTestEveryDays(), doc.getRunbookRef(), PlanStatus.valueOf(doc.getStatus()), doc.getCreatedAt(), doc.getUpdatedAt(),
                    doc.getLastExerciseAt(), doc.getLastOutcome() != null ? Outcome.valueOf(doc.getLastOutcome()) : null, doc.getLastPassedAt(), doc.getLastPassedMinutes(),
                    doc.getLastPassedDataLossMinutes(),
                    doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
