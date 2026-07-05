package com.khetisetu.event.notifications.config;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoRepositories(
    basePackages = "com.khetisetu.event.logs.repository",
    mongoTemplateRef = "logsMongoTemplate"
)
public class LogsMongoConfig {

    private static final Logger log = LoggerFactory.getLogger(LogsMongoConfig.class);

    @Value("${spring.data.mongodb.logs.uri}")
    private String logsUri;

    @Value("${spring.data.mongodb.logs.database}")
    private String logsDatabase;

    /**
     * Retention for log documents, enforced by a MongoDB TTL index on {@code timestamp}.
     */
    @Value("${logs.retention-days:90}")
    private long retentionDays;

    @Bean(name = "logsMongoClient")
    public MongoClient logsMongoClient() {
        return MongoClients.create(logsUri);
    }

    @Bean(name = "logsMongoTemplate")
    public MongoTemplate logsMongoTemplate() {
        return new MongoTemplate(logsMongoClient(), logsDatabase);
    }

    /**
     * Ensures the indexes the log query paths rely on, plus the TTL retention index.
     * Idempotent: createIndex is a no-op when an identical index already exists; the
     * TTL index is recreated if the retention period changed.
     */
    @Bean
    public ApplicationRunner logsIndexInitializer(@Qualifier("logsMongoTemplate") MongoTemplate template) {
        return args -> {
            try {
                MongoCollection<Document> logs = template.getCollection("logs");

                ensureTtlIndex(logs);
                logs.createIndex(Indexes.compoundIndex(Indexes.ascending("category"), Indexes.descending("timestamp")),
                        new IndexOptions().name("category_ts"));
                logs.createIndex(Indexes.compoundIndex(Indexes.ascending("level"), Indexes.descending("timestamp")),
                        new IndexOptions().name("level_ts"));
                logs.createIndex(Indexes.ascending("action"), new IndexOptions().name("action_1"));
                logs.createIndex(Indexes.ascending("actor.id"), new IndexOptions().name("actor_id_1"));
                logs.createIndex(Indexes.ascending("traceId"),
                        new IndexOptions().name("trace_id_1").sparse(true));

                log.info("Logs collection indexes ensured (retention {} days)", retentionDays);
            } catch (Exception e) {
                // Index setup must never prevent the service from starting.
                log.error("Failed to ensure logs indexes: {}", e.getMessage(), e);
            }
        };
    }

    private void ensureTtlIndex(MongoCollection<Document> logs) {
        IndexOptions ttlOptions = new IndexOptions()
                .name("timestamp_ttl")
                .expireAfter(TimeUnit.DAYS.toSeconds(retentionDays), TimeUnit.SECONDS);
        try {
            logs.createIndex(Indexes.ascending("timestamp"), ttlOptions);
        } catch (MongoCommandException e) {
            // IndexOptionsConflict (85) → an index with the same name but a different
            // expireAfter exists (retention changed). Recreate it.
            if (e.getErrorCode() == 85) {
                log.info("Retention changed; recreating TTL index with {} days", retentionDays);
                logs.dropIndex("timestamp_ttl");
                logs.createIndex(Indexes.ascending("timestamp"), ttlOptions);
            } else {
                throw e;
            }
        }
    }
}
