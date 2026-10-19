package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateRecoveryPlanException;
import com.thinklab.domain.exception.InvalidRecoveryPlanStatusException;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryPlan.Strategy;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryPlanDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryPlanDocument.RecoveryPlanPersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RecoveryPlanMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/dr_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<RecoveryPlanDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("dr_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_dr_registry_db")).thenReturn(database);
        when(database.getCollection("recovery_plans", RecoveryPlanDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private RecoveryPlan plan() {
        RecoveryPlan plan = RecoveryPlan.createNew(UUID.randomUUID(), org, "Nightly", UUID.randomUUID(), Strategy.WARM_STANDBY, 120, 30, 7, "KB-1", "op");
        plan.pause("op");
        return plan;
    }

    private RecoveryExercise exercise(ExerciseKind kind, Outcome outcome) {
        Instant now = Instant.now();
        return RecoveryExercise.createNew(UUID.randomUUID(), org, UUID.randomUUID(), kind, outcome, now.minusSeconds(600), now.minusSeconds(60), null,
                outcome != Outcome.SUCCEEDED ? FailureReason.OTHER : null, "tool", now);
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(RecoveryPlan found) {
        FindPublisher<RecoveryPlanDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<RecoveryPlanDocument> subscriber = invocation.getArgument(0);
            Flux.just(RecoveryPlanPersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("a plan survives the round trip, with its summary of exercises and its audit trail")
    void roundTrip() {
        RecoveryPlan plan = plan();
        Instant at = Instant.parse("2026-10-07T10:00:00Z");
        RecoveryPlan withExercises = RecoveryPlan.reconstitute(plan.getId(), org, plan.getName(), plan.getAssetId(), Strategy.PILOT_LIGHT, 120, 30, 7, "KB-1", PlanStatus.PAUSED, at, at,
                at, Outcome.PARTIAL, at, 15, 3, plan.getAuditTrail());

        RecoveryPlanDocument document = RecoveryPlanPersistenceMapper.toDocument(withExercises);
        RecoveryPlan back = RecoveryPlanPersistenceMapper.toDomain(document);

        assertEquals(PlanStatus.PAUSED, back.getStatus());
        assertEquals(Strategy.PILOT_LIGHT, back.getStrategy());
        assertEquals(at, back.getLastExerciseAt());
        assertEquals(Outcome.PARTIAL, back.getLastOutcome());
        assertEquals(at, back.getLastPassedAt());
        assertEquals(15, back.getLastPassedMinutes());
        assertEquals(3, back.getLastPassedDataLossMinutes());
        assertEquals(7, back.getTestEveryDays());
        assertEquals("KB-1", back.getRunbookRef());
        assertEquals(2, back.getAuditTrail().size());
        assertNull(back.getAuditTrail().get(0).fromStatus());
        assertEquals(PlanStatus.ACTIVE, back.getAuditTrail().get(1).fromStatus());
        assertEquals("Nightly", document.getName());
        assertEquals("PAUSED", document.getStatus());
        assertEquals("PARTIAL", document.getLastOutcome());
        assertEquals(org, document.getOrganisationId());
        assertEquals(plan.getId(), document.getId());
        assertEquals(plan.getAssetId(), document.getAssetId());
        assertEquals("PILOT_LIGHT", document.getStrategy());
        assertEquals(120, document.getRtoMinutes());
        assertEquals(30, document.getRpoMinutes());
        assertEquals(at, document.getCreatedAt());
        assertEquals(at, document.getUpdatedAt());
        assertEquals(2, document.getAuditTrail().size());
    }

    @Test
    @DisplayName("a plan never exercised round-trips with an empty summary and no runbook")
    void roundTripWithoutExercises() {
        RecoveryPlan plan = RecoveryPlan.createNew(UUID.randomUUID(), org, "Bare", UUID.randomUUID(), Strategy.ACTIVE_ACTIVE, 5, 0, 30, null, "op");

        RecoveryPlanDocument document = RecoveryPlanPersistenceMapper.toDocument(plan);
        RecoveryPlan back = RecoveryPlanPersistenceMapper.toDomain(document);

        assertNull(document.getLastOutcome());
        assertNull(document.getRunbookRef());
        assertNull(back.getLastOutcome());
        assertNull(back.getLastExerciseAt());
        assertNull(back.getLastPassedAt());
        assertNull(back.getLastPassedMinutes());
        assertNull(back.getLastPassedDataLossMinutes());
        assertNull(back.getRunbookRef());
    }

    @Test
    @DisplayName("the document setters are plain, and the persistence mapper is a utility class")
    void plainDocument() throws ReflectiveOperationException {
        RecoveryPlanDocument document = new RecoveryPlanDocument();
        document.setRunbookRef(null);
        document.setLastPassedMinutes(null);
        document.setLastPassedDataLossMinutes(null);
        document.setLastPassedAt(null);
        document.setLastExerciseAt(null);
        document.setTestEveryDays(9);
        document.setAuditTrail(new ArrayList<>());
        assertNull(document.getRunbookRef());
        assertNull(document.getLastPassedMinutes());
        assertNull(document.getLastPassedDataLossMinutes());
        assertNull(document.getLastPassedAt());
        assertNull(document.getLastExerciseAt());
        assertEquals(9, document.getTestEveryDays());
        assertTrue(document.getAuditTrail().isEmpty());

        Constructor<RecoveryPlanPersistenceMapper> constructor = RecoveryPlanPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create and the lookups are scoped to the organisation, the list oldest first")
    void lookups() {
        RecoveryPlan plan = plan();
        when(collection.insertOne(any(RecoveryPlanDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(plan);
        RecoveryPlanMongoRepositoryAdapter adapter = new RecoveryPlanMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(plan)).expectNext(plan).verifyComplete();
        StepVerifier.create(adapter.findById(plan.getId(), org)).assertNext(found -> assertEquals(plan.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
    }

    @Test
    @DisplayName("a duplicate name is a 409 of the domain, on create or on a rename; any other write error passes through; the URI may omit the database")
    void duplicates() {
        RecoveryPlan plan = plan();
        when(collection.insertOne(any(RecoveryPlanDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        RecoveryPlanMongoRepositoryAdapter adapter = new RecoveryPlanMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(plan)).expectError(DuplicateRecoveryPlanException.class).verify();
        StepVerifier.create(adapter.create(plan)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(plan, PlanStatus.ACTIVE, plan.getAuditTrail().get(1))).expectError(DuplicateRecoveryPlanException.class).verify();
        StepVerifier.create(adapter.save(plan, PlanStatus.ACTIVE, plan.getAuditTrail().get(1))).expectError(MongoWriteException.class).verify();
        assertThrows(NullPointerException.class, () -> new RecoveryPlanMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("save is guarded by the status loaded and pushes the audit entry; a lost race is a conflict")
    void save() {
        RecoveryPlan plan = plan();
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        RecoveryPlanMongoRepositoryAdapter adapter = new RecoveryPlanMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(plan, PlanStatus.ACTIVE, plan.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(plan, PlanStatus.ACTIVE, plan.getAuditTrail().get(1))).expectError(InvalidRecoveryPlanStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("ACTIVE") && guard.getAllValues().get(0).toString().contains("organisationId"));
        assertTrue(update.getAllValues().get(0).toString().contains("auditTrail"));
    }

    @Test
    @DisplayName("an exercise moves the last exercise forward only when it finished later; one that did not pass writes nothing else")
    void recordNotPassed() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        RecoveryPlanMongoRepositoryAdapter adapter = new RecoveryPlanMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.recordExercise(UUID.randomUUID(), org, exercise(ExerciseKind.FAILOVER, Outcome.FAILED))).verifyComplete();
        StepVerifier.create(adapter.recordExercise(UUID.randomUUID(), org, exercise(ExerciseKind.TABLETOP, Outcome.PARTIAL))).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(filter.capture(), update.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("lastExerciseAt"));
        assertTrue(update.getAllValues().get(0).toString().contains("lastOutcome"));
        assertTrue(update.getAllValues().get(0).toString().contains("FAILED"));
        assertTrue(update.getAllValues().get(1).toString().contains("PARTIAL"));
        assertFalse(update.getAllValues().get(0).toString().contains("lastPassedAt"));
    }

    @Test
    @DisplayName("a passed exercise also records itself as the last pass, with how long it took and what it lost, only when newer")
    void recordPassed() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        RecoveryPlanMongoRepositoryAdapter adapter = new RecoveryPlanMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.recordExercise(UUID.randomUUID(), org, exercise(ExerciseKind.FULL_RECOVERY, Outcome.SUCCEEDED))).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(filter.capture(), update.capture());
        assertFalse(update.getAllValues().get(0).toString().contains("lastPassedAt"));
        assertTrue(filter.getAllValues().get(1).toString().contains("lastPassedAt"));
        assertTrue(update.getAllValues().get(1).toString().contains("lastPassedMinutes"));
        assertTrue(update.getAllValues().get(1).toString().contains("lastPassedDataLossMinutes"));
    }
}
