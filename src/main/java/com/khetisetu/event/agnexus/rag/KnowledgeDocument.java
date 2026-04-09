package com.khetisetu.event.agnexus.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Represents a knowledge document in the RAG knowledge base.
 * Contains text content, category metadata, and vector embeddings
 * for similarity search via MongoDB Atlas Vector Search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "knowledge_base")
public class KnowledgeDocument {

    @Id
    private String id;

    /** Document title */
    @TextIndexed(weight = 3)
    private String title;

    /** Full document content */
    @TextIndexed
    private String content;

    /** Category of the document */
    @Indexed
    private KnowledgeCategory category;

    /** Searchable tags */
    @Indexed
    private List<String> tags;

    /** Vector embedding for similarity search (768 dims for text-embedding-004) */
    private List<Double> embedding;

    /** Language of the document */
    @Builder.Default
    private String language = "en";

    /** Source of the document */
    private String source;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum KnowledgeCategory {
        CROP_DISEASE,
        FARMING_PRACTICE,
        MARKET_INFO,
        GOVERNMENT_SCHEME,
        PEST_CONTROL,
        SOIL_HEALTH,
        WEATHER_ADVISORY,
        GENERAL
    }
}
