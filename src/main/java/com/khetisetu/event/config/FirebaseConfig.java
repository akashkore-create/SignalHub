package com.khetisetu.event.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("classpath:firebase-adminsdk.json")
    private Resource serviceAccountResource;

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            logger.info("Firebase Application already initialized");
            return;
        }

        try {
            InputStream serviceAccount = null;

            if (StringUtils.hasText(credentialsPath)) {
                logger.info("Loading Firebase credentials from path: {}", credentialsPath);
                serviceAccount = new FileInputStream(credentialsPath);
            } else if (serviceAccountResource.exists()) {
                logger.info("Loading Firebase credentials from classpath: firebase-adminsdk.json");
                serviceAccount = serviceAccountResource.getInputStream();
            } else {
                logger.warn(
                        "No Firebase credentials found (firebase-adminsdk.json). Push notifications will fail if attempted.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            logger.info("Firebase Application initialized successfully");

        } catch (IOException e) {
            logger.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }
}
