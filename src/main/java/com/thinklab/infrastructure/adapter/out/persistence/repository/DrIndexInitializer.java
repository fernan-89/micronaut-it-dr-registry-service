package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the indexes at startup. The UNIQUE ones are the atomic backstops (ADR-033): a plan name per organisation, and one exercise per
 * {@code (organisation, plan, kind, start)} (ADR-030: the same report twice is the same exercise). The exercise list index serves the real query, the
 * newest exercises of a plan. Fail-open: {@code createIndex} is idempotent, a failure is logged and the application still starts. Turn it off with
 * {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class DrIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String PLAN_NAME_INDEX = "organisationId_1_name_1";
    static final String EXERCISE_KEY_INDEX = "organisationId_1_planId_1_kind_1_startedAt_1";
    static final String EXERCISE_LIST_INDEX = "organisationId_1_planId_1_startedAt_-1";

    private static final Logger log = LoggerFactory.getLogger(DrIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public DrIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    DrIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : RecoveryPlanMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        ensureIndex(RecoveryPlanMongoRepositoryAdapter.COLLECTION_NAME, PLAN_NAME_INDEX, new Document("organisationId", 1).append("name", 1), new IndexOptions().unique(true));
        String exercises = RecoveryExerciseMongoRepositoryAdapter.COLLECTION_NAME;
        ensureIndex(exercises, EXERCISE_KEY_INDEX, new Document("organisationId", 1).append("planId", 1).append("kind", 1).append("startedAt", 1), new IndexOptions().unique(true));
        ensureIndex(exercises, EXERCISE_LIST_INDEX, new Document("organisationId", 1).append("planId", 1).append("startedAt", -1), new IndexOptions());
    }

    private void ensureIndex(String collection, String indexName, Document keys, IndexOptions options) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(collection).createIndex(keys, options.name(indexName))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", indexName, database, collection);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", indexName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", indexName, database, collection, e.getMessage());
        }
    }
}
