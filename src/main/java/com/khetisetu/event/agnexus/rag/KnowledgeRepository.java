package com.khetisetu.event.agnexus.rag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

/**
 * MongoDB repository for the knowledge base documents.
 * Supports text search as a fallback for when Atlas Vector Search is not available.
 */
public interface KnowledgeRepository extends MongoRepository<KnowledgeDocument, String> {

    List<KnowledgeDocument> findByCategory(KnowledgeDocument.KnowledgeCategory category);

    List<KnowledgeDocument> findByTagsContaining(String tag);

    @Query("{ $text: { $search: ?0 } }")
    List<KnowledgeDocument> fullTextSearch(String searchTerm);

    Page<KnowledgeDocument> findByCategoryOrderByCreatedAtDesc(
            KnowledgeDocument.KnowledgeCategory category, Pageable pageable);

    long countByCategory(KnowledgeDocument.KnowledgeCategory category);
}
