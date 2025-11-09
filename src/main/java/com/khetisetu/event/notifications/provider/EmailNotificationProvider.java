package com.khetisetu.event.notifications.provider;

import com.khetisetu.event.notifications.model.EmailSenderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationProvider.class);

    @Autowired
    private BrevoEmailProvider brevoEmailProvider;

    /**
     * Sends an email using Brevo API.
     */
    public void sendEmail(EmailSenderConfig config, String to, String subject, String content) throws Exception {
        if (config == null) {
            logger.error("Email configuration is null");
            throw new Exception("Email configuration cannot be null");
        }

        try {
            brevoEmailProvider.sendTransactionalEmail(
                    config.getSenderEmail(),
                    config.getSenderName(),
                    to,
                    subject,
                    content
            );
            logger.info("Email sent to {}", to);
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Optional: create and schedule a Brevo campaign.
     */
    public void createCampaign(String name, String subject, String htmlContent, int[] listIds, String scheduleAt) throws Exception {
        try {
            brevoEmailProvider.createCampaign(
                    name,
                    subject,
                    "Khetisetu Team",
                    "akash@khetisetu.com", // Must be a verified sender or domain
                    htmlContent,
                    listIds,
                    scheduleAt
            );
        } catch (Exception e) {
            logger.error("Failed to create Brevo campaign: {}", e.getMessage(), e);
            throw e;
        }
    }
}
