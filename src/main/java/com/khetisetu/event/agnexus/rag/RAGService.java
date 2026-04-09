package com.khetisetu.event.agnexus.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core RAG (Retrieval-Augmented Generation) service.
 * Combines vector similarity search with MongoDB Atlas Vector Search
 * and text-based fallback search to provide contextual documents
 * for augmenting LLM prompts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {

    private final KnowledgeRepository knowledgeRepo;
    private final EmbeddingService embeddingService;
    private final MongoTemplate mongoTemplate;

    @Value("${rag.search.top-k:5}")
    private int defaultTopK;

    @Value("${rag.search.min-score:0.7}")
    private double minScore;

    /**
     * Retrieve relevant documents using vector similarity search.
     * Falls back to text search if vector search is unavailable.
     *
     * @param query    user's query
     * @param category optional category filter
     * @param topK     number of results to return
     * @return list of relevant knowledge documents
     */
    public List<KnowledgeDocument> retrieveRelevantDocuments(String query,
                                                              KnowledgeDocument.KnowledgeCategory category,
                                                              int topK) {
        // Try vector search first
        List<Double> queryEmbedding = embeddingService.generateEmbedding(query);

        if (!queryEmbedding.isEmpty()) {
            try {
                List<KnowledgeDocument> results = vectorSearch(queryEmbedding, category, topK);
                if (!results.isEmpty()) {
                    log.info("Vector search returned {} results for query: '{}'", results.size(),
                            query.substring(0, Math.min(50, query.length())));
                    return results;
                }
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to text search: {}", e.getMessage());
            }
        }

        // Fallback to text search
        return textSearch(query, category, topK);
    }

    /**
     * Retrieve documents with default settings.
     */
    public List<KnowledgeDocument> retrieveRelevantDocuments(String query) {
        return retrieveRelevantDocuments(query, null, defaultTopK);
    }

    /**
     * Build an augmented prompt by injecting retrieved knowledge context.
     *
     * @param query     the user's query
     * @param documents retrieved knowledge documents
     * @return formatted context string for prompt injection
     */
    public String buildAugmentedContext(String query, List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("=== Relevant Knowledge Context ===\n\n");

        for (int i = 0; i < documents.size(); i++) {
            KnowledgeDocument doc = documents.get(i);
            context.append(String.format("[%d] %s (%s)\n", i + 1, doc.getTitle(), doc.getCategory()));
            context.append(doc.getContent()).append("\n\n");
        }

        context.append("=== End of Knowledge Context ===\n\n");
        context.append("Use the above context to provide accurate, knowledge-backed responses. ");
        context.append("If the context is not relevant to the query, rely on your general knowledge.\n\n");

        return context.toString();
    }

    /**
     * Ingest a new document into the knowledge base with embeddings.
     *
     * @param title    document title
     * @param content  document content
     * @param category document category
     * @param tags     searchable tags
     * @return the saved document
     */
    public KnowledgeDocument ingestDocument(String title, String content,
                                             KnowledgeDocument.KnowledgeCategory category,
                                             List<String> tags) {
        List<Double> embedding = embeddingService.generateEmbedding(title + " " + content);

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .title(title)
                .content(content)
                .category(category)
                .tags(tags)
                .embedding(embedding)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        KnowledgeDocument saved = knowledgeRepo.save(doc);
        log.info("Ingested knowledge document: '{}' (category={}, embeddingSize={})",
                title, category, embedding.size());
        return saved;
    }

    /**
     * Bulk ingest multiple documents.
     */
    public List<KnowledgeDocument> bulkIngest(List<KnowledgeDocument> documents) {
        List<KnowledgeDocument> saved = new ArrayList<>();
        for (KnowledgeDocument doc : documents) {
            try {
                List<Double> embedding = embeddingService.generateEmbedding(doc.getTitle() + " " + doc.getContent());
                doc.setEmbedding(embedding);
                doc.setCreatedAt(Instant.now());
                doc.setUpdatedAt(Instant.now());
                saved.add(knowledgeRepo.save(doc));
            } catch (Exception e) {
                log.error("Failed to ingest document: '{}'", doc.getTitle(), e);
            }
        }
        log.info("Bulk ingested {}/{} documents", saved.size(), documents.size());
        return saved;
    }

    /**
     * Delete a knowledge document.
     */
    public void deleteDocument(String documentId) {
        knowledgeRepo.deleteById(documentId);
        log.info("Deleted knowledge document: {}", documentId);
    }

    /**
     * Search knowledge base using text query.
     */
    public List<KnowledgeDocument> searchKnowledge(String query, KnowledgeDocument.KnowledgeCategory category) {
        return retrieveRelevantDocuments(query, category, defaultTopK);
    }

    /**
     * Get total count of knowledge documents.
     */
    public long getKnowledgeBaseSize() {
        return knowledgeRepo.count();
    }

    // ========== Private Helpers ==========

    /**
     * Execute MongoDB Atlas Vector Search using $vectorSearch aggregation stage.
     */
    private List<KnowledgeDocument> vectorSearch(List<Double> queryEmbedding,
                                                   KnowledgeDocument.KnowledgeCategory category,
                                                   int topK) {
        // Build the $vectorSearch aggregation pipeline using raw BSON documents
        Document vectorSearchStage = new Document("$vectorSearch",
                new Document("index", "knowledge_vector_index")
                        .append("path", "embedding")
                        .append("queryVector", queryEmbedding)
                        .append("numCandidates", topK * 10)
                        .append("limit", topK)
        );

        // Add filter by category if specified
        if (category != null) {
            vectorSearchStage.get("$vectorSearch", Document.class)
                    .append("filter", new Document("category", category.name()));
        }

        // Add score field
        Document addScoreStage = new Document("$addFields",
                new Document("score", new Document("$meta", "vectorSearchScore")));

        // Filter by minimum score
        Document matchScoreStage = new Document("$match",
                new Document("score", new Document("$gte", minScore)));

        List<Document> pipeline = Arrays.asList(vectorSearchStage, addScoreStage, matchScoreStage);

        try {
            List<KnowledgeDocument> results = new ArrayList<>();
            mongoTemplate.getCollection("knowledge_base")
                    .aggregate(pipeline)
                    .forEach(doc -> {
                        KnowledgeDocument kDoc = mongoTemplate.getConverter()
                                .read(KnowledgeDocument.class, doc);
                        results.add(kDoc);
                    });
            return results;
        } catch (Exception e) {
            log.warn("Atlas Vector Search not available (may need index configuration): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback text-based search using MongoDB text index.
     */
    private List<KnowledgeDocument> textSearch(String query,
                                                 KnowledgeDocument.KnowledgeCategory category,
                                                 int topK) {
        try {
            List<KnowledgeDocument> results;

            if (category != null) {
                // Text search + category filter
                results = knowledgeRepo.fullTextSearch(query).stream()
                        .filter(doc -> doc.getCategory() == category)
                        .limit(topK)
                        .collect(Collectors.toList());
            } else {
                results = knowledgeRepo.fullTextSearch(query).stream()
                        .limit(topK)
                        .collect(Collectors.toList());
            }

            // If text search returns nothing, try tag-based search
            if (results.isEmpty()) {
                String[] keywords = query.toLowerCase().split("\\s+");
                for (String keyword : keywords) {
                    results.addAll(knowledgeRepo.findByTagsContaining(keyword));
                    if (results.size() >= topK) break;
                }
                results = results.stream().distinct().limit(topK).collect(Collectors.toList());
            }

            log.info("Text search returned {} results for query: '{}'", results.size(),
                    query.substring(0, Math.min(50, query.length())));
            return results;

        } catch (Exception e) {
            log.warn("Text search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
