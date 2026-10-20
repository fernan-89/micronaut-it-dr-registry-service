package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateRecoveryPlanRequest;
import com.thinklab.application.dto.request.ReportRecoveryExerciseRequest;
import com.thinklab.application.dto.request.UpdateRecoveryPlanRequest;
import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.RecoveryPlanResponse;
import com.thinklab.application.dto.response.RecoveryExerciseResponse;
import com.thinklab.application.usecase.RetrievePromisesUseCase;
import com.thinklab.application.usecase.ControlRecoveryPlanUseCase;
import com.thinklab.application.usecase.InitiateRecoveryPlanUseCase;
import com.thinklab.application.usecase.ReportRecoveryExerciseUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryPlansUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryPlanAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryPlanUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryExerciseUseCase;
import com.thinklab.application.usecase.RetrieveRecoveryExercisesUseCase;
import com.thinklab.application.usecase.UpdateRecoveryPlanUseCase;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Readiness;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-dr-registry} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.RecoveryPlan} is the Control Record, at the root;
 * {@link com.thinklab.domain.model.RecoveryExercise} is the secondary aggregate, under {@code /exercise}: facts reported by the people and tools that ran them, never edited, never
 * deleted. There is no {@code DELETE}: a plan is paused.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} scopes every lookup (another tenant's plan is a 404) and
 * {@code X-Role} makes every route refuse a {@code REQUESTER} with 403 {@code ERR-DRR-00403}.
 */
@Controller("/it-dr-registry/v1")
public class DrRegistryController {

    private static final Logger log = LoggerFactory.getLogger(DrRegistryController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateRecoveryPlanUseCase initiatePlan;
    private final RetrieveRecoveryPlanUseCase retrievePlan;
    private final RetrieveRecoveryPlansUseCase retrievePlans;
    private final UpdateRecoveryPlanUseCase updatePlan;
    private final ControlRecoveryPlanUseCase controlPlan;
    private final RetrieveRecoveryPlanAuditLogUseCase planAuditLog;
    private final ReportRecoveryExerciseUseCase reportExercise;
    private final RetrieveRecoveryExerciseUseCase retrieveExercise;
    private final RetrieveRecoveryExercisesUseCase retrieveExercises;

    private final RetrievePromisesUseCase retrievePromises;

    public DrRegistryController(InitiateRecoveryPlanUseCase initiatePlan, RetrieveRecoveryPlanUseCase retrievePlan, RetrieveRecoveryPlansUseCase retrievePlans,
                                    UpdateRecoveryPlanUseCase updatePlan, ControlRecoveryPlanUseCase controlPlan, RetrieveRecoveryPlanAuditLogUseCase planAuditLog,
                                    ReportRecoveryExerciseUseCase reportExercise, RetrieveRecoveryExerciseUseCase retrieveExercise, RetrieveRecoveryExercisesUseCase retrieveExercises,
            RetrievePromisesUseCase retrievePromises) {
        this.retrievePromises = retrievePromises;
        this.initiatePlan = initiatePlan;
        this.retrievePlan = retrievePlan;
        this.retrievePlans = retrievePlans;
        this.updatePlan = updatePlan;
        this.controlPlan = controlPlan;
        this.planAuditLog = planAuditLog;
        this.reportExercise = reportExercise;
        this.retrieveExercise = retrieveExercise;
        this.retrieveExercises = retrieveExercises;
    }

    // ------------------------------------------------------------------ Plans

    /** Behavior Qualifier: {@code initiate}. How an asset will be recovered. */
    @Post("/initiate")
    public Mono<HttpResponse<RecoveryPlanResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateRecoveryPlanRequest request
    ) {
        log.info("[ACTION: INITIATE_RECOVERY_PLAN] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiatePlan.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. One plan of the tenant, judged now. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<RecoveryPlanResponse>> retrieve(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrievePlan.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection), oldest first, filterable by status, asset and readiness. */
    @Get("/retrieve")
    public Mono<List<RecoveryPlanResponse>> retrieveAll(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                        @QueryValue @Nullable PlanStatus status, @QueryValue @Nullable UUID assetId, @QueryValue @Nullable Readiness readiness) {
        return Mono.defer(() -> retrievePlans.execute(UUID.fromString(tenantId), status, assetId, readiness, role).collectList());
    }

    /**
     * Behavior Qualifier: {@code promises/retrieve} (platform ADR-001, the promises contract). Every broken promise and every one that changed within the days asked
     * (30 unless asked, 1 to 365), at most {@code limit} of each (500 unless asked, at most 1000).
     */
    @Get("/promises/retrieve")
    public Mono<List<PromiseResponse>> promises(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                @QueryValue @Nullable Integer changedWithinDays, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrievePromises.execute(UUID.fromString(tenantId), changedWithinDays, limit, role));
    }

    /** Behavior Qualifier: {@code update}. The whole definition again. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateRecoveryPlanRequest request
    ) {
        return Mono.defer(() -> updatePlan.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the plan is no longer judged. */
    @Put("/{id}/control/pause")
    public Mono<HttpResponse<Void>> pause(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                          @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlRecoveryPlanUseCase.Action.PAUSE, executor, role);
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    @Put("/{id}/control/resume")
    public Mono<HttpResponse<Void>> resume(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                           @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlRecoveryPlanUseCase.Action.RESUME, executor, role);
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> auditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> planAuditLog.execute(id, UUID.fromString(tenantId), role));
    }

    // ------------------------------------------------------------------ Exercises

    /** Behavior Qualifier: {@code exercise/initiate}. What is reported when an exercise of a plan ends: 201 for a new exercise, 200 for one already reported. */
    @Post("/exercise/initiate")
    public Mono<HttpResponse<RecoveryExerciseResponse>> reportExercise(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ReportRecoveryExerciseRequest request
    ) {
        log.info("[ACTION: REPORT_RECOVERY_EXERCISE] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return reportExercise.execute(UUID.fromString(tenantId), request, executor, role)
                .map(reported -> reported.created() ? HttpResponse.created(reported.exercise()) : HttpResponse.ok(reported.exercise()));
    }

    /** Behavior Qualifier: {@code exercise/retrieve}. One exercise of the tenant. */
    @Get("/exercise/{id}/retrieve")
    public Mono<HttpResponse<RecoveryExerciseResponse>> retrieveExercise(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveExercise.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code exercise/retrieve} (collection), newest start first, at most 200 (50 unless asked). */
    @Get("/exercise/retrieve")
    public Mono<List<RecoveryExerciseResponse>> retrieveExercises(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                      @QueryValue @Nullable UUID planId, @QueryValue @Nullable ExerciseKind kind, @QueryValue @Nullable Outcome outcome,
                                                      @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrieveExercises.execute(UUID.fromString(tenantId), new RecoveryExerciseRepository.Filter(planId, kind, outcome), limit, role).collectList());
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlRecoveryPlanUseCase.Action action, String executor, String role) {
        log.info("[ACTION: {}_RECOVERY_PLAN] [EXECUTOR: {}] for ID: {}", action, executor, id);

        return Mono.defer(() -> controlPlan.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
