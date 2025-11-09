package com.khetisetu.event.notifications.provider;


import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BrevoEmailProvider {

    private static final Logger logger = LoggerFactory.getLogger(BrevoEmailProvider.class);
    @Value("${brevo.apikey}")
    private String API_KEY; // Replace with your Brevo API key
    private static final String BASE_URL = "https://api.brevo.com/v3";

    private final OkHttpClient client = new OkHttpClient();

    /**
     * Send a transactional email directly to a recipient (most common use case).
     */
    public void sendTransactionalEmail(String fromEmail, String fromName, String toEmail, String subject, String htmlBody) throws Exception {
        JSONObject sender = new JSONObject()
                .put("name", fromName)
                .put("email", fromEmail);

        JSONObject to = new JSONObject()
                .put("email", toEmail);

        JSONObject payload = new JSONObject()
                .put("sender", sender)
                .put("to", new org.json.JSONArray().put(to))
                .put("subject", subject)
                .put("htmlContent", htmlBody)
                .put("textContent", stripHtml(htmlBody));

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload.toString());
        logger.info("Base url {} and api key {}", BASE_URL,API_KEY);
        Request request = new Request.Builder()
                .url(BASE_URL + "/smtp/email")
                .addHeader("api-key", API_KEY)
                .addHeader("accept", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "unknown";
                logger.error("Brevo transactional email failed: {}", error);
                throw new RuntimeException("Brevo transactional email failed: " + error);
            }
            logger.info("Transactional email sent successfully to {}", toEmail);
        }
    }

    /**
     * Create and schedule a marketing campaign (matches the Brevo example you shared).
     */
    public void createCampaign(String name, String subject, String fromName, String fromEmail, String htmlContent, int[] listIds, String scheduleAt) throws Exception {
        JSONObject sender = new JSONObject()
                .put("name", fromName)
                .put("email", fromEmail);

        JSONObject recipients = new JSONObject()
                .put("listIds", listIds);

        JSONObject payload = new JSONObject()
                .put("name", name)
                .put("subject", subject)
                .put("sender", sender)
                .put("type", "classic")
                .put("htmlContent", htmlContent)
                .put("recipients", recipients)
                .put("scheduledAt", scheduleAt); // format: "YYYY-MM-DD HH:mm:ss"

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload.toString());

        Request request = new Request.Builder()
                .url(BASE_URL + "/emailCampaigns")
                .addHeader("api-key", API_KEY)
                .addHeader("accept", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "unknown";
                logger.error("Brevo campaign creation failed: {}", error);
                throw new RuntimeException("Brevo campaign creation failed: " + error);
            }
            logger.info("Brevo campaign created successfully: {}", name);
        }
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "");
    }
}
