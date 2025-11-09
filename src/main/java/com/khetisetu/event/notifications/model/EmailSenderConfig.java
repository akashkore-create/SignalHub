package com.khetisetu.event.notifications.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "email_sender_config")
public class EmailSenderConfig extends BaseModel {
    @Indexed(unique = true)
    private String category;
    private String senderEmail; // Used for the "From" field in the email
    private String senderName;  // Display name for the "From" field
    private String smtpHost;   // SMTP server host (e.g., smtp.hostinger.com)
    private int smtpPort;      // SMTP server port (e.g., 587)
    private String username;   // SMTP username for authentication
    private String password;   // SMTP password for authentication
    private String protocol;   // Protocol (e.g., smtp)
}