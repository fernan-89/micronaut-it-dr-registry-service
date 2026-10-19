package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.model.RecoveryExercise.ExerciseKind;
import com.thinklab.domain.repository.RecoveryExerciseRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryExerciseDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryExerciseDocument.RecoveryExercisePersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the RecoveryExercise aggregate, raw reactive-streams driver. Exercises are only ever inserted. The unique
 * {@code (organisationId, planId, kind, startedAt)} index is what makes a repeated report the same exercise: the loser of an insert reads the
 * stored one (ADR-030).
 */
@Singleton
public class RecoveryExerciseMongoRepositoryAdapter implements RecoveryExerciseRepository {

    static final String COLLECTION_NAME = "recovery_exercises";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public RecoveryExerciseMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : RecoveryPlanMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<RecoveryExerciseDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, RecoveryExerciseDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Stored> create(RecoveryExercise exercise) {
        return Mono.from(getCollection().insertOne(RecoveryExercisePersistenceMapper.toDocument(exercise)))
                .map(result -> new Stored(exercise, true))
                .onErrorResume(MongoWriteException.class, error -> error.getError().getCode() == RecoveryPlanMongoRepositoryAdapter.DUPLICATE_KEY
                        ? findByNaturalKey(exercise.getOrganisationId(), exercise.getPlanId(), exercise.getKind(), exercise.getStartedAt()).map(existing -> new Stored(existing, false))
                        : Mono.error(error));
    }

    @Override
    public Mono<RecoveryExercise> findByNaturalKey(UUID organisationId, UUID planId, ExerciseKind kind, Instant startedAt) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.eq("planId", planId),
                        Filters.eq("kind", kind.name()), Filters.eq("startedAt", startedAt))).first())
                .map(RecoveryExercisePersistenceMapper::toDomain);
    }

    @Override
    public Mono<RecoveryExercise> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(RecoveryExercisePersistenceMapper::toDomain);
    }

    @Override
    public Flux<RecoveryExercise> findAll(UUID organisationId, Filter filter, int limit) {
        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq(FIELD_ORGANISATION, organisationId));
        if (filter.planId() != null) {
            conditions.add(Filters.eq("planId", filter.planId()));
        }
        if (filter.kind() != null) {
            conditions.add(Filters.eq("kind", filter.kind().name()));
        }
        if (filter.outcome() != null) {
            conditions.add(Filters.eq("outcome", filter.outcome().name()));
        }
        return Flux.from(getCollection().find(Filters.and(conditions)).sort(Sorts.descending("startedAt")).limit(limit)).map(RecoveryExercisePersistenceMapper::toDomain);
    }
}
