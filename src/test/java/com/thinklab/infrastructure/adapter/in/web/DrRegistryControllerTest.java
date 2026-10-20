package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateRecoveryPlanRequest;
import com.thinklab.application.dto.request.ReportRecoveryExerciseRequest;
import com.thinklab.application.dto.request.UpdateRecoveryPlanRequest;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.application.dto.response.RecoveryExerciseResponse;
import com.thinklab.application.usecase.ControlRecoveryPlanUseCase;
import com.thinklab.application.usecase.InitiateRecoveryPlanUseCase;
import com.thinklab.application.usecase.ReportRecoveryExerciseUseCase;
import com.thinklab.application.usecase.ReportRecoveryExerciseUseCase.Reported;
import com.thinklab.application.usecase.RetrieveRecoveryPlansUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryPlanAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryPlanUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryExerciseUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryExercisesUseCase;
import com.thinklab.application.usecase.RetrievePromisesUseCase;
import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.usecase.UpdateRecoveryPlanUseCase;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Readiness;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Filter;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant, executor and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class DrRegistryControllerTest {

    private static final String EXECUTOR = "op-1";
    private static final String ROLE = "ENGINEER";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");

    @Mock private InitiateRecoveryPlanUseCase initiatePlan;
    @Mock private RetrieveRecoveryPlanUseCase retrievePlan;
    @Mock private RetrieveRecoveryPlansUseCase retrievePlans;
    @Mock private UpdateRecoveryPlanUseCase updatePlan;
    @Mock private ControlRecoveryPlanUseCase controlPlan;
    @Mock private RetrieveRecoveryPlanAuditLogUseCase planAuditLog;
    @Mock private ReportRecoveryExerciseUseCase reportExercise;
    @Mock private RetrieveRecoveryExerciseUseCase retrieveExercise;
    @Mock private RetrieveRecoveryExercisesUseCase retrieveExercises;
    @Mock private RetrievePromisesUseCase retrievePromises;

    private DrRegistryController controller() {
        return new DrRegistryController(initiatePlan, retrievePlan, retrievePlans, updatePlan, controlPlan, planAuditLog, reportExercise, retrieveExercise, retrieveExercises, retrievePromises);
    }

    private RecoveryPlanResponse planResponse() {
        return new RecoveryPlanResponse(id, tenant, "Nightly", UUID.randomUUID(), "WARM_STANDBY", 120, 30, 30, null, "ACTIVE", "NEVER_TESTED", now, null, null, null, null, null, null, null, null,
                now, now);
    }

    private RecoveryExerciseResponse runResponse() {
        return new RecoveryExerciseResponse(id, tenant, UUID.randomUUID(), "FAILOVER", "SUCCEEDED", now, now, 1, null, null, "tool", now);
    }

    @Test
    @DisplayName("initiate answers 201 with the plan")
    void initiate() {
        InitiateRecoveryPlanRequest request = new InitiateRecoveryPlanRequest("Nightly", UUID.randomUUID(), Strategy.WARM_STANDBY, 120, 30, 30, null);
        when(initiatePlan.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(planResponse()));

        StepVerifier.create(controller().initiate(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve and the list answer the use cases' result with the filters")
    void retrieve() {
        when(retrievePlan.execute(id, tenant, ROLE)).thenReturn(Mono.just(planResponse()));
        when(retrievePlans.execute(tenant, PlanStatus.ACTIVE, null, Readiness.READY, ROLE)).thenReturn(Flux.just(planResponse()));

        StepVerifier.create(controller().retrieve(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveAll(tenantHeader, ROLE, PlanStatus.ACTIVE, null, Readiness.READY)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, pause and resume answer 204 and pass the right action")
    void changes() {
        UpdateRecoveryPlanRequest request = new UpdateRecoveryPlanRequest("N", UUID.randomUUID(), Strategy.WARM_STANDBY, 120, 30, 30, null);
        when(updatePlan.execute(id, tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlPlan.execute(id, tenant, ControlRecoveryPlanUseCase.Action.PAUSE, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlPlan.execute(id, tenant, ControlRecoveryPlanUseCase.Action.RESUME, EXECUTOR, ROLE)).thenReturn(Mono.empty());

        StepVerifier.create(controller().update(id, tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().pause(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().resume(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("the audit log answers the trail")
    void auditLog() {
        when(planAuditLog.execute(id, tenant, ROLE)).thenReturn(Mono.just(List.of(new AuditEntryResponse(now, "INITIATED", EXECUTOR, null, "ACTIVE", null))));

        StepVerifier.create(controller().auditLog(id, tenantHeader, ROLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("a new exercise is 201 and one already reported is 200")
    void reportRunStatus() {
        ReportRecoveryExerciseRequest request = new ReportRecoveryExerciseRequest(UUID.randomUUID(), ExerciseKind.FAILOVER, Outcome.SUCCEEDED, now, now, null, null);
        when(reportExercise.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(new Reported(runResponse(), true))).thenReturn(Mono.just(new Reported(runResponse(), false)));

        StepVerifier.create(controller().reportExercise(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().reportExercise(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("one exercise and the exercise list, with the filter and the limit")
    void exercises() {
        UUID planId = UUID.randomUUID();
        when(retrieveExercise.execute(id, tenant, ROLE)).thenReturn(Mono.just(runResponse()));
        when(retrieveExercises.execute(tenant, new Filter(planId, ExerciseKind.FAILOVER, Outcome.FAILED), 10, ROLE)).thenReturn(Flux.just(runResponse()));

        StepVerifier.create(controller().retrieveExercise(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveExercises(tenantHeader, ROLE, planId, ExerciseKind.FAILOVER, Outcome.FAILED, 10)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("the promises route passes the tenant, the role and the two parameters (platform ADR-001)")
    void promises() {
        when(retrievePromises.execute(tenant, 7, 50, ROLE)).thenReturn(Mono.just(List.of(new PromiseResponse(UUID.randomUUID(), "P", null, "KEPT", null, "ACTIVE", now))));

        StepVerifier.create(controller().promises(tenantHeader, ROLE, 7, 50)).assertNext(promises -> assertEquals(1, promises.size())).verifyComplete();
    }
}
