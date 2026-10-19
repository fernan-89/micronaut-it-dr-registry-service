package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.FailureReason;
import com.thinklab.domain.model.RecoveryExercise.Outcome;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.repository.RecoveryExerciseRepository.Filter;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryExerciseDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryExerciseDocument.RecoveryExercisePersistenceMapper;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RecoveryExerciseMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/dr_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<RecoveryExerciseDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("dr_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_dr_registry_db")).thenReturn(database);
        when(database.getCollection("recovery_exercises", RecoveryExerciseDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private RecoveryExercise exercise(Outcome outcome) {
        Instant now = Instant.now();
        return RecoveryExercise.createNew(UUID.randomUUID(), org, UUID.randomUUID(), ExerciseKind.FAILOVER, outcome, now.minusSeconds(600), now.minusSeconds(60), 42,
                outcome != Outcome.SUCCEEDED ? FailureReason.TIMEOUT : null, "tool", now);
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private FindPublisher<RecoveryExerciseDocument> finds(RecoveryExercise found) {
        FindPublisher<RecoveryExerciseDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        when(publisher.limit(anyInt())).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<RecoveryExerciseDocument> subscriber = invocation.getArgument(0);
            Flux.just(RecoveryExercisePersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    @Test
    @DisplayName("an exercise survives the round trip, with or without a failure reason")
    void roundTrip() {
        RecoveryExercise failed = exercise(Outcome.FAILED);
        RecoveryExerciseDocument document = RecoveryExercisePersistenceMapper.toDocument(failed);
        RecoveryExercise back = RecoveryExercisePersistenceMapper.toDomain(document);

        assertEquals(FailureReason.TIMEOUT, back.getFailureReason());
        assertEquals("TIMEOUT", document.getFailureReason());
        assertEquals("FAILOVER", document.getKind());
        assertEquals("FAILED", document.getOutcome());
        assertEquals(42, document.getDataLossMinutes());
        assertEquals(org, document.getOrganisationId());
        assertEquals(failed.getPlanId(), document.getPlanId());
        assertEquals(failed.getId(), document.getId());
        assertEquals(failed.getStartedAt(), document.getStartedAt());
        assertEquals(failed.getFinishedAt(), document.getFinishedAt());
        assertEquals("tool", document.getReportedBy());
        assertEquals(failed.getReportedAt(), document.getReportedAt());

        RecoveryExercise ok = RecoveryExercisePersistenceMapper.toDomain(RecoveryExercisePersistenceMapper.toDocument(exercise(Outcome.SUCCEEDED)));
        assertNull(ok.getFailureReason());
        assertTrue(ok.succeeded());
    }

    @Test
    @DisplayName("the persistence mapper is a utility class")
    void mapperIsUtility() throws ReflectiveOperationException {
        Constructor<RecoveryExercisePersistenceMapper> constructor = RecoveryExercisePersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create answers the exercise as created; the lookups are scoped to the organisation")
    void lookups() {
        RecoveryExercise exercise = exercise(Outcome.SUCCEEDED);
        when(collection.insertOne(any(RecoveryExerciseDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(exercise);
        RecoveryExerciseMongoRepositoryAdapter adapter = new RecoveryExerciseMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(exercise)).assertNext(stored -> assertTrue(stored.created())).verifyComplete();
        StepVerifier.create(adapter.findById(exercise.getId(), org)).assertNext(found -> assertEquals(exercise.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findByNaturalKey(org, exercise.getPlanId(), ExerciseKind.FAILOVER, exercise.getStartedAt())).expectNextCount(1).verifyComplete();
        assertThrows(NullPointerException.class, () -> new RecoveryExerciseMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("a duplicate key means the exercise was already reported: the stored one comes back, not created; any other error passes through")
    void duplicate() {
        RecoveryExercise exercise = exercise(Outcome.SUCCEEDED);
        when(collection.insertOne(any(RecoveryExerciseDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        finds(exercise);
        RecoveryExerciseMongoRepositoryAdapter adapter = new RecoveryExerciseMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(exercise)).assertNext(stored -> {
            assertFalse(stored.created());
            assertEquals(exercise.getId(), stored.exercise().getId());
        }).verifyComplete();
        StepVerifier.create(adapter.create(exercise)).expectError(MongoWriteException.class).verify();
    }

    @Test
    @DisplayName("the list is filtered only by what is given, newest start first, and limited")
    void list() {
        RecoveryExercise exercise = exercise(Outcome.SUCCEEDED);
        FindPublisher<RecoveryExerciseDocument> publisher = finds(exercise);
        RecoveryExerciseMongoRepositoryAdapter adapter = new RecoveryExerciseMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.findAll(org, new Filter(null, null, null), 5)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(org, new Filter(exercise.getPlanId(), ExerciseKind.FAILOVER, Outcome.FAILED), 7)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        String bare = filter.getAllValues().get(0).toString();
        String full = filter.getAllValues().get(1).toString();
        assertTrue(bare.contains("organisationId") && !bare.contains("planId") && !bare.contains("outcome"));
        assertTrue(full.contains("planId") && full.contains("kind") && full.contains("FAILED"));
        verify(publisher).limit(5);
        verify(publisher).limit(7);
    }
}
