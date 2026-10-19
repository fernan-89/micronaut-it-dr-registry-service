package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateRecoveryPlanException;
import com.thinklab.domain.exception.InvalidRecoveryPlanStatusException;
import com.thinklab.domain.model.RecoveryPlan;
import com.thinklab.domain.model.RecoveryPlan.PlanAuditEntry;
import com.thinklab.domain.model.RecoveryPlan.PlanStatus;
import com.thinklab.domain.model.RecoveryExercise;
import com.thinklab.domain.repository.RecoveryPlanRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryPlanDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryPlanDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.RecoveryPlanDocument.RecoveryPlanPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the RecoveryPlan aggregate, raw reactive-streams driver. Every change is one atomic update guarded by
 * the status loaded (ADR-033) that also appends the audit entry; a name unique per organisation is the atomic backstop of a duplicate. The
 * summary of the reported exercises only ever moves forward in time (a compare on the finish of the last exercise, of the last one that passed and of the last one that measured a recovery), so it does not matter in
 * which order, or how many times, the reports arrive (ADR-030).
 */
@Singleton
public class RecoveryPlanMongoRepositoryAdapter implements RecoveryPlanRepository {

    private static final Logger log = LoggerFactory.getLogger(RecoveryPlanMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_dr_registry_db";
    static final String COLLECTION_NAME = "recovery_plans";
    static final int DUPLICATE_KEY = 11000;
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LAST_EXERCISE = "lastExerciseAt";
    private static final String FIELD_LAST_PASSED = "lastPassedAt";
    private static final String FIELD_LAST_MEASURED = "lastMeasuredAt";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public RecoveryPlanMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<RecoveryPlanDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, RecoveryPlanDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<RecoveryPlan> create(RecoveryPlan plan) {
        log.debug("[PERSISTENCE] Monolithic create for RecoveryPlan Aggregate: {}", plan.getId());

        return Mono.from(getCollection().insertOne(RecoveryPlanPersistenceMapper.toDocument(plan)))
                .map(result -> plan)
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, plan));
    }

    @Override
    public Mono<RecoveryPlan> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(RecoveryPlanPersistenceMapper::toDomain);
    }

    @Override
    public Flux<RecoveryPlan> findAll(UUID organisationId) {
        return Flux.from(getCollection().find(Filters.eq(FIELD_ORGANISATION, organisationId)).sort(Sorts.ascending("createdAt"))).map(RecoveryPlanPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(RecoveryPlan plan, PlanStatus expectedStatus, PlanAuditEntry auditEntry) {
        Bson guard = Filters.and(Filters.eq(FIELD_ID, plan.getId()), Filters.eq(FIELD_ORGANISATION, plan.getOrganisationId()), Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("name", plan.getName()),
                Updates.set("assetId", plan.getAssetId()),
                Updates.set("strategy", plan.getStrategy().name()),
                Updates.set("rtoMinutes", plan.getRtoMinutes()),
                Updates.set("rpoMinutes", plan.getRpoMinutes()),
                Updates.set("testEveryDays", plan.getTestEveryDays()),
                Updates.set("runbookRef", plan.getRunbookRef()),
                Updates.set(FIELD_STATUS, plan.getStatus().name()),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidRecoveryPlanStatusException("RecoveryPlan was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty())
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, plan));
    }

    @Override
    public Mono<Void> recordExercise(UUID planId, UUID organisationId, RecoveryExercise exercise) {
        Bson owner = Filters.and(Filters.eq(FIELD_ID, planId), Filters.eq(FIELD_ORGANISATION, organisationId));
        // The last exercise and what came of it only move forward: written only when this one finished later than the one the plan remembers.
        Bson newest = Filters.and(owner, Filters.or(Filters.eq(FIELD_LAST_EXERCISE, null), Filters.lt(FIELD_LAST_EXERCISE, exercise.getFinishedAt())));
        Mono<Void> last = Mono.from(getCollection().updateOne(newest, Updates.combine(Updates.set(FIELD_LAST_EXERCISE, exercise.getFinishedAt()),
                Updates.set("lastOutcome", exercise.getOutcome().name())))).then();
        if (!exercise.succeeded()) {
            return last;
        }
        // The last one that passed, likewise.
        Bson newerPass = Filters.and(owner, Filters.or(Filters.eq(FIELD_LAST_PASSED, null), Filters.lt(FIELD_LAST_PASSED, exercise.getFinishedAt())));
        Mono<Void> passed = last.then(Mono.from(getCollection().updateOne(newerPass, Updates.set(FIELD_LAST_PASSED, exercise.getFinishedAt()))).then());
        if (!exercise.measured()) {
            return passed;
        }
        // The last pass that measured a recovery (a failover or a full recovery), how long it took and how much data it lost, likewise.
        Bson newerMeasure = Filters.and(owner, Filters.or(Filters.eq(FIELD_LAST_MEASURED, null), Filters.lt(FIELD_LAST_MEASURED, exercise.getFinishedAt())));
        Bson measured = Updates.combine(Updates.set(FIELD_LAST_MEASURED, exercise.getFinishedAt()), Updates.set("lastMeasuredMinutes", exercise.durationMinutes()),
                Updates.set("lastMeasuredDataLossMinutes", exercise.getDataLossMinutes()));
        return passed.then(Mono.from(getCollection().updateOne(newerMeasure, measured))).then();
    }

    private static Throwable duplicate(MongoWriteException error, RecoveryPlan plan) {
        return error.getError().getCode() == DUPLICATE_KEY
                ? new DuplicateRecoveryPlanException("A recovery plan named '" + plan.getName() + "' already exists in this organisation.") : error;
    }
}
