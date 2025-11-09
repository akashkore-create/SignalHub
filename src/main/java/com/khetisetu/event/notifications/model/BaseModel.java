package com.khetisetu.event.notifications.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;

@Document
@Data
public class BaseModel {
    @Id
    public String id;
    @CreatedDate
    public Instant createdAt;
    @LastModifiedDate
    public Instant updatedAt;
    public HashMap<String, String> attrs = new HashMap<>();

}
