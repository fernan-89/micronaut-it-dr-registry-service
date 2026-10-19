package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateRecoveryPlanRequest;
import com.thinklab.application.dto.request.ReportRecoveryExerciseRequest;
import com.thinklab.application.dto.request.UpdateRecoveryPlanRequest;
import com.thinklab.domain.exception.DrAccessDeniedException;
import com.thinklab.domain.exception.RecoveryPlanNotFoundException;
import com.thinklab.domain.exception.RecoveryExerciseNotFoundException;
import com.thinklab.domain.exception.InvalidRecoveryPlanStatusException;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Readiness;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Filter;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Stored;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrUseCasesTest {

    private static final String STAFF = "ENGINEER";
    private static final String REQUESTER = "REQUESTER";

    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private HashServicePort hash;
    private RecoveryPlanRepository plans;
    private RecoveryExerciseRepository exercises;

    @BeforeEach
    void setUp() {
        hash = mock(HashServicePort.class);
        plans = mock(RecoveryPlanRepository.class);
        exercises = mock(RecoveryExerciseRepository.class);
    }

    private RecoveryPlan plan() {
        return RecoveryPlan.createNew(UUID.randomUUID(), org, "Nightly", asset, Strategy.WARM_STANDBY, 120, 30, 7, "KB-1", "op");
    }

    private InitiateRecoveryPlanRequest initiateRequest() {
        return new InitiateRecoveryPlanRequest("Nightly", asset, Strategy.WARM_STANDBY, 120, 30, 7, "KB-1");
    }

    private UpdateRecoveryPlanRequest updateRequest() {
        return new UpdateRecoveryPlanRequest("Renamed", asset, Strategy.ACTIVE_ACTIVE, 60, 0, 7, null);
    }

    private ReportRecoveryExerciseRequest report(UUID planId) {
        return new ReportRecoveryExerciseRequest(planId, ExerciseKind.FAILOVER, Outcome.SUCCEEDED, now.minusSeconds(1800), now.minusSeconds(60), 10, null);
    }

    @Test
    @DisplayName("the access rule refuses a requester and lets staff and a missing role through; it is a utility class")
    void access() throws ReflectiveOperationException {
        DrAccess.requireStaff(STAFF, "x");
        DrAccess.requireStaff(null, "x");
        DrAccessDeniedException denied = assertThrows(DrAccessDeniedException.class, () -> DrAccess.requireStaff(REQUESTER, "do it"));
        assertTrue(denied.getMessage().contains("do it"));
        Constructor<DrAccess> constructor = DrAccess.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("initiate spends a sovereign id, stores the plan and answers it judged now; a requester spends nothing")
    void initiate() {
        UUID id = UUID.randomUUID();
        when(hash.generateSovereignId("recovery-plan-creation")).thenReturn(Mono.just(id));
        when(plans.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        InitiateRecoveryPlanUseCase useCase = new InitiateRecoveryPlanUseCase(hash, plans, clock);

        StepVerifier.create(useCase.execute(org, initiateRequest(), "op", STAFF)).assertNext(response -> {
            assertEquals(id, response.id());
            assertEquals("ACTIVE", response.status());
            assertEquals("NEVER_TESTED", response.readiness());
        }).verifyComplete();

        StepVerifier.create(useCase.execute(org, initiateRequest(), "op", REQUESTER)).expectError(DrAccessDeniedException.class).verify();
        verify(hash).generateSovereignId("recovery-plan-creation");
    }

    @Test
    @DisplayName("retrieve answers the tenant's plan, a 404 when it is not there, and refuses a requester")
    void retrieve() {
        RecoveryPlan plan = plan();
        when(plans.findById(plan.getId(), org)).thenReturn(Mono.just(plan));
        when(plans.findById(any(), eq(UUID.fromString("00000000-0000-0000-0000-000000000001")))).thenReturn(Mono.empty());
        RetrieveRecoveryPlanUseCase useCase = new RetrieveRecoveryPlanUseCase(plans, clock);

        StepVerifier.create(useCase.execute(plan.getId(), org, STAFF)).assertNext(response -> assertEquals("Nightly", response.name())).verifyComplete();
        StepVerifier.create(useCase.execute(plan.getId(), UUID.fromString("00000000-0000-0000-0000-000000000001"), STAFF)).expectError(RecoveryPlanNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(plan.getId(), org, REQUESTER)).expectError(DrAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the list filters by status, asset and readiness, judged now, and refuses a requester")
    void retrieveAll() {
        RecoveryPlan active = plan();
        RecoveryPlan paused = RecoveryPlan.createNew(UUID.randomUUID(), org, "Other", UUID.randomUUID(), Strategy.BACKUP_RESTORE, 120, 30, 30, null, "op");
        paused.pause("op");
        when(plans.findAll(org)).thenReturn(Flux.just(active, paused));
        RetrieveRecoveryPlansUseCase useCase = new RetrieveRecoveryPlansUseCase(plans, clock);

        StepVerifier.create(useCase.execute(org, null, null, null, STAFF)).expectNextCount(2).verifyComplete();
        StepVerifier.create(useCase.execute(org, PlanStatus.PAUSED, null, null, STAFF)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, asset, null, STAFF)).assertNext(response -> assertEquals(active.getId(), response.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, Readiness.PAUSED, STAFF)).assertNext(response -> assertEquals(paused.getId(), response.id())).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, Readiness.OBJECTIVE_MISSED, STAFF)).verifyComplete();
        StepVerifier.create(useCase.execute(org, null, null, null, REQUESTER)).expectError(DrAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("update and control load the plan, apply the change and save it with the status loaded; an unknown plan is a 404 and a requester is refused")
    void updateAndControl() {
        RecoveryPlan plan = plan();
        when(plans.findById(plan.getId(), org)).thenReturn(Mono.just(plan));
        when(plans.findById(any(), eq(UUID.fromString("00000000-0000-0000-0000-000000000001")))).thenReturn(Mono.empty());
        when(plans.save(any(), any(), any())).thenReturn(Mono.empty());
        RecoveryPlanWorkflow workflow = new RecoveryPlanWorkflow(plans);
        UpdateRecoveryPlanUseCase update = new UpdateRecoveryPlanUseCase(workflow);
        ControlRecoveryPlanUseCase control = new ControlRecoveryPlanUseCase(workflow);

        StepVerifier.create(update.execute(plan.getId(), org, updateRequest(), "op", STAFF)).verifyComplete();
        assertEquals("Renamed", plan.getName());
        StepVerifier.create(control.execute(plan.getId(), org, ControlRecoveryPlanUseCase.Action.PAUSE, "op", STAFF)).verifyComplete();
        assertEquals(PlanStatus.PAUSED, plan.getStatus());
        StepVerifier.create(control.execute(plan.getId(), org, ControlRecoveryPlanUseCase.Action.PAUSE, "op", STAFF)).expectError(InvalidRecoveryPlanStatusException.class).verify();
        StepVerifier.create(control.execute(plan.getId(), org, ControlRecoveryPlanUseCase.Action.RESUME, "op", STAFF)).verifyComplete();
        assertEquals(PlanStatus.ACTIVE, plan.getStatus());

        ArgumentCaptor<PlanStatus> expected = ArgumentCaptor.forClass(PlanStatus.class);
        verify(plans, times(3)).save(any(), expected.capture(), any());
        assertEquals(PlanStatus.ACTIVE, expected.getAllValues().get(0));
        assertEquals(PlanStatus.ACTIVE, expected.getAllValues().get(1));
        assertEquals(PlanStatus.PAUSED, expected.getAllValues().get(2));

        UUID elsewhere = UUID.fromString("00000000-0000-0000-0000-000000000001");
        StepVerifier.create(update.execute(plan.getId(), elsewhere, updateRequest(), "op", STAFF)).expectError(RecoveryPlanNotFoundException.class).verify();
        StepVerifier.create(update.execute(plan.getId(), org, updateRequest(), "op", REQUESTER)).expectError(DrAccessDeniedException.class).verify();
        StepVerifier.create(control.execute(plan.getId(), org, ControlRecoveryPlanUseCase.Action.PAUSE, "op", REQUESTER)).expectError(DrAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the audit log is the plan's trail, a 404 for an unknown plan, and staff only")
    void auditLog() {
        RecoveryPlan plan = plan();
        plan.pause("op");
        when(plans.findById(plan.getId(), org)).thenReturn(Mono.just(plan));
        when(plans.findById(any(), eq(UUID.fromString("00000000-0000-0000-0000-000000000001")))).thenReturn(Mono.empty());
        RetrieveRecoveryPlanAuditLogUseCase useCase = new RetrieveRecoveryPlanAuditLogUseCase(plans);

        StepVerifier.create(useCase.execute(plan.getId(), org, STAFF)).assertNext(entries -> {
            assertEquals(2, entries.size());
            assertEquals("INITIATED", entries.get(0).action());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(plan.getId(), UUID.fromString("00000000-0000-0000-0000-000000000001"), STAFF)).expectError(RecoveryPlanNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(plan.getId(), org, REQUESTER)).expectError(DrAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("a new exercise is stored and folded into the plan; the answer says it was created")
    void reportNew() {
        RecoveryPlan plan = plan();
        UUID exerciseId = UUID.randomUUID();
        when(plans.findById(plan.getId(), org)).thenReturn(Mono.just(plan));
        when(exercises.findByNaturalKey(eq(org), eq(plan.getId()), eq(ExerciseKind.FAILOVER), any())).thenReturn(Mono.empty());
        when(hash.generateSovereignId("recovery-exercise-creation")).thenReturn(Mono.just(exerciseId));
        when(exercises.create(any())).thenAnswer(invocation -> Mono.just(new Stored(invocation.getArgument(0), true)));
        when(plans.recordExercise(eq(plan.getId()), eq(org), any())).thenReturn(Mono.empty());
        ReportRecoveryExerciseUseCase useCase = new ReportRecoveryExerciseUseCase(hash, plans, exercises, clock);

        StepVerifier.create(useCase.execute(org, report(plan.getId()), "tool", STAFF)).assertNext(reported -> {
            assertTrue(reported.created());
            assertEquals(exerciseId, reported.exercise().id());
            assertEquals("tool", reported.exercise().reportedBy());
        }).verifyComplete();
        verify(plans).recordExercise(eq(plan.getId()), eq(org), any(RecoveryExercise.class));
    }

    @Test
    @DisplayName("the same exercise reported again is the stored one, no id is spent, and the plan summary is folded again")
    void reportAgain() {
        RecoveryPlan plan = plan();
        RecoveryExercise stored = RecoveryExercise.createNew(UUID.randomUUID(), org, plan.getId(), ExerciseKind.FAILOVER, Outcome.FAILED, now.minusSeconds(1800), now.minusSeconds(60), null,
                FailureReason.TIMEOUT, "tool", now);
        when(plans.findById(plan.getId(), org)).thenReturn(Mono.just(plan));
        when(exercises.findByNaturalKey(eq(org), eq(plan.getId()), eq(ExerciseKind.FAILOVER), any())).thenReturn(Mono.just(stored));
        when(plans.recordExercise(any(), any(), any())).thenReturn(Mono.empty());
        ReportRecoveryExerciseUseCase useCase = new ReportRecoveryExerciseUseCase(hash, plans, exercises, clock);

        StepVerifier.create(useCase.execute(org, report(plan.getId()), "tool", STAFF)).assertNext(reported -> {
            assertEquals(false, reported.created());
            assertEquals(stored.getId(), reported.exercise().id());
            assertEquals("TIMEOUT", reported.exercise().failureReason());
        }).verifyComplete();
        verify(hash, never()).generateSovereignId(any());
        verify(exercises, never()).create(any());
        verify(plans).recordExercise(eq(plan.getId()), eq(org), eq(stored));
    }

    @Test
    @DisplayName("an exercise for a plan that is not the tenant's is a 404, a requester is refused, and an exercise that makes no sense is a 400")
    void reportRefused() {
        UUID unknown = UUID.randomUUID();
        when(plans.findById(unknown, org)).thenReturn(Mono.empty());
        RecoveryPlan plan = plan();
        when(plans.findById(plan.getId(), org)).thenReturn(Mono.just(plan));
        when(exercises.findByNaturalKey(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(hash.generateSovereignId(any())).thenReturn(Mono.just(UUID.randomUUID()));
        ReportRecoveryExerciseUseCase useCase = new ReportRecoveryExerciseUseCase(hash, plans, exercises, clock);

        StepVerifier.create(useCase.execute(org, report(unknown), "tool", STAFF)).expectError(RecoveryPlanNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(org, report(unknown), "tool", REQUESTER)).expectError(DrAccessDeniedException.class).verify();
        ReportRecoveryExerciseRequest failedWithoutReason = new ReportRecoveryExerciseRequest(plan.getId(), ExerciseKind.FAILOVER, Outcome.FAILED, now.minusSeconds(60), now.minusSeconds(30), null, null);
        StepVerifier.create(useCase.execute(org, failedWithoutReason, "tool", STAFF)).expectError(IllegalArgumentException.class).verify();
        verify(exercises, never()).create(any());
    }

    @Test
    @DisplayName("one exercise: answered for the tenant, a 404 otherwise, staff only")
    void retrieveExercise() {
        RecoveryExercise exercise = RecoveryExercise.createNew(UUID.randomUUID(), org, UUID.randomUUID(), ExerciseKind.FULL_RECOVERY, Outcome.SUCCEEDED, now.minusSeconds(600), now.minusSeconds(60), 5, null,
                "tool", now);
        when(exercises.findById(exercise.getId(), org)).thenReturn(Mono.just(exercise));
        when(exercises.findById(any(), eq(UUID.fromString("00000000-0000-0000-0000-000000000001")))).thenReturn(Mono.empty());
        RetrieveRecoveryExerciseUseCase useCase = new RetrieveRecoveryExerciseUseCase(exercises);

        StepVerifier.create(useCase.execute(exercise.getId(), org, STAFF)).assertNext(response -> assertEquals("FULL_RECOVERY", response.kind())).verifyComplete();
        StepVerifier.create(useCase.execute(exercise.getId(), UUID.fromString("00000000-0000-0000-0000-000000000001"), STAFF)).expectError(RecoveryExerciseNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(exercise.getId(), org, REQUESTER)).expectError(DrAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("the exercise list uses 50 unless asked, accepts 1 to 200, refuses anything else and a requester")
    void retrieveExercises() {
        RecoveryExercise exercise = RecoveryExercise.createNew(UUID.randomUUID(), org, UUID.randomUUID(), ExerciseKind.FAILOVER, Outcome.SUCCEEDED, now.minusSeconds(600), now.minusSeconds(60), null, null,
                "tool", now);
        Filter filter = new Filter(null, null, null);
        when(exercises.findAll(eq(org), eq(filter), any(Integer.class))).thenReturn(Flux.just(exercise));
        RetrieveRecoveryExercisesUseCase useCase = new RetrieveRecoveryExercisesUseCase(exercises);

        StepVerifier.create(useCase.execute(org, filter, null, STAFF)).expectNextCount(1).verifyComplete();
        verify(exercises).findAll(org, filter, 50);
        StepVerifier.create(useCase.execute(org, filter, 1, STAFF)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(org, filter, 200, STAFF)).expectNextCount(1).verifyComplete();
        verify(exercises).findAll(org, filter, 200);
        StepVerifier.create(useCase.execute(org, filter, 0, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(org, filter, 201, STAFF)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(org, filter, null, REQUESTER)).expectError(DrAccessDeniedException.class).verify();
    }
}
