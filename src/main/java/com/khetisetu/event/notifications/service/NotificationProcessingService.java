// src/main/java/com/khetisetu/event/notifications/service/NotificationProcessingService.java
package com.khetisetu.event.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.model.NotificationTemplate;
import com.khetisetu.event.notifications.provider.EmailNotificationProvider;
import com.khetisetu.event.notifications.repository.NotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.khetisetu.event.notifications.constants.Constants.NOTIFICATION_PREFIX;


@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessingService {

    private static final long RATE_LIMIT_MS = 60_000L;
    private static final String DEFAULT_LANGUAGE = "en";
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("en", "mr", "hn", "kn", "ta", "te");

    private final EmailNotificationProvider emailProvider;
    private final NotificationRepository notificationRepository;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    private final Map<String, NotificationTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSentTime = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadTemplates() throws IOException {
        for (String lang : SUPPORTED_LANGUAGES) {
            ClassPathResource resource = new ClassPathResource("templates/" + lang + "/notification_templates.json");
            if (resource.exists()) {
                List<NotificationTemplate> list = objectMapper.readValue(
                        resource.getInputStream(),
                        new TypeReference<List<NotificationTemplate>>() {}
                );
                list.forEach(t -> templates.put(lang + "_" + t.getName(), t));
                log.info("Loaded {} templates for language: {}", list.size(), lang);
            } else {
                log.warn("No templates found for language: {}", lang);
            }
        }
        log.info("Total templates loaded: {}", templates.size());
    }

    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void process(NotificationEvent event) {
        String recipient = event.recipient();
        String language = event.language() != null && SUPPORTED_LANGUAGES.contains(event.language())
                ? event.language() : DEFAULT_LANGUAGE;

        if (!canSendNotification(recipient)) {
            log.warn("Rate limit exceeded for {}", recipient);
            return;
        }

        Notification notification = createNotification(
                event.type(), recipient, event.templateName(), event.params(), language
        );

        try {
            NotificationTemplate template = getTemplate(event.templateName(), language);

            if ("EMAIL".equalsIgnoreCase(event.type())) {
                String subject = renderSubject(template.getSubject(), event.params());
                String content = renderTemplate(template.getName(), event.params(), language);
                emailProvider.sendEmail(event.senderConfig(), recipient, subject, content); // config from env later
                updateStatus(notification, "SENT", null);
            }

            else if ("PUSH".equalsIgnoreCase(event.type())) {
                // Push handled separately or via userId lookup if needed
                log.info("PUSH not implemented in this version (requires user lookup)");
                updateStatus(notification, "SKIPPED", "Push requires user subscription");
            }

            else if ("SMS".equalsIgnoreCase(event.type())) {
                log.info("SMS not implemented");
                updateStatus(notification, "SKIPPED", "SMS not implemented");
            }

        } catch (Exception e) {
            updateStatus(notification, "FAILED", e.getMessage());
            log.error("Failed to send {} notification to {}", event.type(), recipient, e);
        }
    }

    private NotificationTemplate getTemplate(String name, String language) {
        NotificationTemplate template = templates.get(language + "_" + name);
        if (template == null) {
            template = templates.get(DEFAULT_LANGUAGE + "_" + name);
        }
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + name + " for language: " + language);
        }
        return template;
    }

    private String renderTemplate(String templateName, Map<String, String> params, String language) {
        NotificationTemplate template = getTemplate(templateName, language);
        if ("EMAIL".equals(template.getType())) {
            Context context = new Context(new Locale(language));
            params.forEach(context::setVariable);
            return templateEngine.process(language + "/" + templateName, context);
        } else {
            String content = template.getContent();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return content;
        }
    }

    private String renderSubject(String subject, Map<String, String> params) {
        if (subject == null) return "Notification";
        for (Map.Entry<String, String> e : params.entrySet()) {
            subject = subject.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return subject;
    }

    private Notification createNotification(String type, String recipient, String templateName,
                                            Map<String, String> params, String language) {
        NotificationTemplate template = getTemplate(templateName, language);
        Notification n = new Notification();
        n.setId(NOTIFICATION_PREFIX + Instant.now().toEpochMilli());
        n.setType(type);
        n.setRecipient(recipient);
        n.setSubject(renderSubject(template.getSubject(), params));
        n.setContent(renderTemplate(templateName, params, language));
        n.setStatus("PENDING");
        n.setTemplateName(templateName);
        n.setRetryCount(0);
        n.setCreatedAt(Instant.now());
        n.setUpdatedAt(Instant.now());
        return notificationRepository.save(n);
    }

    private void updateStatus(Notification n, String status, String error) {
        n.setStatus(status);
        n.setErrorMessage(error);
        if ("FAILED".equals(status)) n.setRetryCount(n.getRetryCount() + 1);
        n.setUpdatedAt(Instant.now());
        notificationRepository.save(n);
    }

    private boolean canSendNotification(String recipient) {
        long now = System.currentTimeMillis();
        Long last = lastSentTime.get(recipient);
        if (last == null || (now - last) > RATE_LIMIT_MS) {
            lastSentTime.put(recipient, now);
            return true;
        }
        return false;
    }
}