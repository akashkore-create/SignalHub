package com.khetisetu.event.notifications.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Primary MongoDB configuration for the main khetisetu database.
 * Provides the default {@code mongoTemplate} bean used by repositories
 * like {@link com.khetisetu.event.notifications.repository.NotificationRepository}.
 *
 * <p>This is needed because {@link LogsMongoConfig} defines a custom MongoClient,
 * which prevents Spring Boot from auto-configuring the default mongoTemplate.</p>
 */
@Configuration
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
