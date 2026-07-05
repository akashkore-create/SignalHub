package com.khetisetu.event.logs.service;

import com.khetisetu.event.logs.LogCategory;
import com.khetisetu.event.notifications.model.logs.Log;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-side query and analytics service over the logs collection.
 * Builds dynamic, index-friendly queries and the aggregation behind the
 * admin dashboard's stats endpoint.
 */
@Service
@Slf4j
public class LogQueryService {

    private final MongoTemplate mongoTemplate;

    public LogQueryService(@Qualifier("logsMongoTemplate") MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Paginated log search.
     *
     * @param categories   optional list of categories (see {@link LogCategory}); matched against
     *                     the category field OR, for legacy documents, the action prefix
     * @param actionPrefix optional literal action prefix (regex-escaped)
     * @param levels       optional levels; defaults to INFO/WARN/ERROR/FATAL
     * @param search       optional case-insensitive text match on details/action/actor.name
     * @param actorId      optional exact actor id
     * @param from/to      optional time window
     */
    public Page<Log> search(List<String> categories, String actionPrefix, List<String> levels,
                            String search, String actorId, Instant from, Instant to,
                            Pageable pageable) {
        Query query = new Query(buildCriteria(categories, actionPrefix, levels, search, actorId, from, to))
                .with(pageable)
                .with(Sort.by(Sort.Order.desc("timestamp")));

        List<Log> content = mongoTemplate.find(query, Log.class, "logs");
        long total = mongoTemplate.count(
                new Query(buildCriteria(categories, actionPrefix, levels, search, actorId, from, to)), "logs");
        return new PageImpl<>(content, pageable, total);
    }

    private Criteria buildCriteria(List<String> categories, String actionPrefix, List<String> levels,
                                   String search, String actorId, Instant from, Instant to) {
        List<Criteria> parts = new ArrayList<>();

        if (levels != null && !levels.isEmpty()) {
            parts.add(Criteria.where("level").in(levels));
        }
        if (categories != null && !categories.isEmpty()) {
            // Match the indexed category field, plus an action-prefix fallback so
            // documents written before the category field existed still match.
            List<Criteria> catOr = new ArrayList<>();
            catOr.add(Criteria.where("category").in(categories));
            List<String> prefixes = categories.stream()
                    .flatMap(c -> LogCategory.prefixesOf(c).stream())
                    .toList();
            if (!prefixes.isEmpty()) {
                String prefixRegex = "^(" + String.join("|", prefixes.stream().map(Pattern::quote).toList()) + ")";
                catOr.add(Criteria.where("category").isNull().and("action").regex(prefixRegex, "i"));
            }
            parts.add(new Criteria().orOperator(catOr));
        }
        if (actionPrefix != null && !actionPrefix.isBlank()) {
            parts.add(Criteria.where("action").regex("^" + Pattern.quote(actionPrefix), "i"));
        }
        if (search != null && !search.isBlank()) {
            String quoted = Pattern.quote(search.trim());
            parts.add(new Criteria().orOperator(
                    Criteria.where("details").regex(quoted, "i"),
                    Criteria.where("action").regex(quoted, "i"),
                    Criteria.where("actor.name").regex(quoted, "i"),
                    Criteria.where("entity.name").regex(quoted, "i"),
                    Criteria.where("traceId").is(search.trim())
            ));
        }
        if (actorId != null && !actorId.isBlank()) {
            parts.add(Criteria.where("actor.id").is(actorId));
        }
        if (from != null || to != null) {
            Criteria time = Criteria.where("timestamp");
            if (from != null) time = time.gte(from);
            if (to != null) time = time.lte(to);
            parts.add(time);
        }

        return parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts);
    }

    /**
     * Dashboard statistics for the last {@code hours} hours: total count, counts by
     * level and category, top actions, an hourly timeline, and the latest errors.
     *
     * @param categories optional category scope (empty = all categories)
     */
    public Map<String, Object> stats(int hours, List<String> categories) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        Document matchDoc = new Document("timestamp", new Document("$gte", since));
        if (categories != null && !categories.isEmpty()) {
            List<Document> catOr = new ArrayList<>();
            catOr.add(new Document("category", new Document("$in", categories)));
            List<String> prefixes = categories.stream()
                    .flatMap(c -> LogCategory.prefixesOf(c).stream())
                    .toList();
            if (!prefixes.isEmpty()) {
                String prefixRegex = "^(" + String.join("|", prefixes.stream().map(Pattern::quote).toList()) + ")";
                catOr.add(new Document("category", null)
                        .append("action", new Document("$regex", prefixRegex).append("$options", "i")));
            }
            matchDoc.append("$or", catOr);
        }
        Document match = new Document("$match", matchDoc);

        // Effective category: stored value, else derived from action prefix (legacy docs).
        Document categoryExpr = buildCategorySwitchExpr();

        Document facet = new Document("$facet", new Document()
                .append("total", List.of(new Document("$count", "count")))
                .append("byLevel", List.of(
                        new Document("$group", new Document("_id", "$level").append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("count", -1))))
                .append("byCategory", List.of(
                        new Document("$addFields", new Document("categoryEff",
                                new Document("$ifNull", List.of("$category", categoryExpr)))),
                        new Document("$group", new Document("_id", "$categoryEff").append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("count", -1))))
                .append("topActions", List.of(
                        new Document("$group", new Document("_id", "$action").append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("count", -1)),
                        new Document("$limit", 10)))
                .append("timeline", List.of(
                        new Document("$group", new Document("_id",
                                new Document("$dateToString", new Document("format", "%Y-%m-%dT%H:00:00Z")
                                        .append("date", "$timestamp")))
                                .append("count", new Document("$sum", 1))
                                .append("errors", new Document("$sum", new Document("$cond",
                                        List.of(new Document("$in", List.of("$level", List.of("ERROR", "FATAL"))), 1, 0))))),
                        new Document("$sort", new Document("_id", 1)))));

        Document result = mongoTemplate.getCollection("logs")
                .aggregate(List.of(match, facet))
                .first();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("windowHours", hours);
        stats.put("since", since.toString());

        long total = 0;
        if (result != null) {
            List<Document> totalDocs = result.getList("total", Document.class);
            total = totalDocs.isEmpty() ? 0 : ((Number) totalDocs.get(0).get("count")).longValue();
            stats.put("byLevel", toCountMap(result.getList("byLevel", Document.class)));
            stats.put("byCategory", toCountMap(result.getList("byCategory", Document.class)));
            stats.put("topActions", result.getList("topActions", Document.class).stream()
                    .map(d -> Map.of("action", String.valueOf(d.get("_id")), "count", d.get("count")))
                    .toList());
            stats.put("timeline", result.getList("timeline", Document.class).stream()
                    .map(d -> Map.of(
                            "hour", String.valueOf(d.get("_id")),
                            "count", d.get("count"),
                            "errors", d.get("errors")))
                    .toList());
        } else {
            stats.put("byLevel", Map.of());
            stats.put("byCategory", Map.of());
            stats.put("topActions", List.of());
            stats.put("timeline", List.of());
        }
        stats.put("total", total);

        Query errorsQuery = new Query(buildCriteria(categories, null, List.of("ERROR", "FATAL"),
                null, null, since, null))
                .with(Sort.by(Sort.Order.desc("timestamp")))
                .limit(5);
        stats.put("recentErrors", mongoTemplate.find(errorsQuery, Log.class, "logs"));

        return stats;
    }

    /**
     * $switch expression mapping action prefixes to categories, mirroring
     * {@link LogCategory#fromAction} for documents without a stored category.
     */
    private Document buildCategorySwitchExpr() {
        List<Document> branches = new ArrayList<>();
        for (String category : LogCategory.all()) {
            List<String> prefixes = LogCategory.prefixesOf(category);
            if (prefixes.isEmpty()) continue;
            String regex = "^(" + String.join("|", prefixes.stream().map(Pattern::quote).toList()) + ")";
            branches.add(new Document("case", new Document("$regexMatch",
                    new Document("input", new Document("$ifNull", List.of("$action", "")))
                            .append("regex", regex)
                            .append("options", "i")))
                    .append("then", category));
        }
        return new Document("$switch", new Document("branches", branches).append("default", LogCategory.SYSTEM));
    }

    private Map<String, Object> toCountMap(List<Document> docs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Document d : docs) {
            map.put(String.valueOf(d.get("_id")), d.get("count"));
        }
        return map;
    }
}
