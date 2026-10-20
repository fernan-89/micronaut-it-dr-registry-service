package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.exception.DrAccessDeniedException;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromisesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private RecoveryPlan plan(String name, Outcome outcome, Instant exercised, Instant passed, Integer minutes) {
        Instant created = NOW.minus(Duration.ofDays(100));
        return RecoveryPlan.reconstitute(UUID.randomUUID(), org, name, asset, Strategy.WARM_STANDBY, 120, 30, 30, null, PlanStatus.ACTIVE, created, created, exercised, outcome, passed,
                minutes == null ? null : passed, minutes, minutes == null ? null : 10, List.of());
    }

    private RetrievePromisesUseCase useCase(RecoveryPlan... found) {
        RecoveryPlanRepository repository = mock(RecoveryPlanRepository.class);
        when(repository.findAll(org)).thenReturn(Flux.just(found));
        return new RetrievePromisesUseCase(repository, clock);
    }

    @Test
    @DisplayName("a plan whose exercise failed is broken with DR_EXERCISE_FAILED; one that passed lately is listed as kept with the pass as when it changed; a plan that is ready and did not change within the days asked is left out")
    void selects() {
        RecoveryPlan failed = plan("Failed", Outcome.FAILED, NOW.minus(Duration.ofDays(40)), NOW.minus(Duration.ofDays(70)), null);
        RecoveryPlan recovered = plan("Recovered", Outcome.SUCCEEDED, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)), 60);
        RecoveryPlan quiet = plan("Quiet", Outcome.SUCCEEDED, NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(10)), 60);

        StepVerifier.create(useCase(quiet, recovered, failed).execute(org, 5, null, "ENGINEER")).assertNext(promises -> {
            assertEquals(List.of("Failed", "Recovered"), promises.stream().map(PromiseResponse::name).toList());
            assertEquals("BROKEN", promises.get(0).verdict());
            assertEquals("DR_EXERCISE_FAILED", promises.get(0).code());
            assertEquals(asset, promises.get(0).assetId());
            assertEquals("ACTIVE", promises.get(0).status());
            assertEquals("KEPT", promises.get(1).verdict());
            assertEquals(NOW.minus(Duration.ofHours(2)), promises.get(1).changedAt());
        }).verifyComplete();
    }

    @Test
    @DisplayName("the window and the limit apply to each kind")
    void window() {
        RecoveryPlan keptOld = plan("OLD", Outcome.SUCCEEDED, NOW.minus(Duration.ofHours(30)), NOW.minus(Duration.ofHours(30)), 60);
        RecoveryPlan kept = plan("NEW", Outcome.SUCCEEDED, NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(5)), 60);
        RecoveryPlan one = plan("B1", Outcome.FAILED, NOW.minus(Duration.ofDays(40)), NOW.minus(Duration.ofDays(70)), null);
        RecoveryPlan two = plan("B2", Outcome.FAILED, NOW.minus(Duration.ofDays(41)), NOW.minus(Duration.ofDays(70)), null);

        StepVerifier.create(useCase(keptOld, kept, one, two).execute(org, 1, 1, "ENGINEER"))
                .assertNext(promises -> assertEquals(List.of("B1", "NEW"), promises.stream().map(PromiseResponse::name).toList())).verifyComplete();
    }

    @Test
    @DisplayName("the days must be 1 to 365 and the limit 1 to 1000; a requester is refused")
    void validation() {
        RetrievePromisesUseCase useCase = useCase();

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 0, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 366, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 0, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 1001, "ENGINEER").block());
        assertThrows(DrAccessDeniedException.class, () -> useCase.execute(org, null, null, "REQUESTER").block());
    }

    @Test
    @DisplayName("the selection is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<PromiseSelection> constructor = PromiseSelection.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}
