package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.DrMapper;
import com.thinklab.domain.exception.RecoveryPlanNotFoundException;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a plan (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveRecoveryPlanAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveRecoveryPlanAuditLogUseCase.class);

    private final RecoveryPlanRepository repository;

    public RetrieveRecoveryPlanAuditLogUseCase(RecoveryPlanRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of RecoveryPlan ID: {}", id);

        return Mono.fromRunnable(() -> DrAccess.requireStaff(role, "read the audit trail of a recovery plan"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new RecoveryPlanNotFoundException("RecoveryPlan " + id + " not found.")))
                .map(plan -> plan.getAuditTrail().stream().map(DrMapper::toResponse).collect(Collectors.toList()));
    }
}
