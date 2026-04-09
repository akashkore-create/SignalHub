package com.khetisetu.event.notifications.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Primary MongoDB configuration for the main khetisetu database.
 * Provides the default {@code mongoTemplate} bean used by repositories
 * like {@link com.khetisetu.event.notifications.repository.NotificationRepository}.
 */
@Configuration
@EnableMongoRepositories(
    basePackages = {
        "com.khetisetu.event.notifications.repository",
        "com.khetisetu.event.agnexus.memory",
        "com.khetisetu.event.agnexus.rag"
    },
    mongoTemplateRef = "mongoTemplate"
)
public class PrimaryMongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Primary
    @Bean(name = "mongoClient")
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Primary
    @Bean(name = "mongoTemplate")
    public MongoTemplate mongoTemplate() {
        // Database name is extracted from the URI (khetisetu)
        return new MongoTemplate(mongoClient(), "khetisetu");
    }
}
