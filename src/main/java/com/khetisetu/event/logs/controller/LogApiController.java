package com.khetisetu.event.logs.controller;

import com.khetisetu.event.logs.service.LogQueryService;
import com.khetisetu.event.notifications.model.logs.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for log read operations.
 * Used by the admin panel in the main khetisetu backend to query logs.
 *
 * <p>Protected by {@link com.khetisetu.event.config.ApiKeyAuthFilter}.</p>
 */
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Slf4j
public class LogApiController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_STATS_WINDOW_HOURS = 24 * 30;

    private final LogQueryService logQueryService;

    /**
     * Gets paginated logs with structured filters.
     *
     * @param category     optional comma-separated categories (USER, ORDER, BOOKING, ...)
     * @param actionPrefix optional literal action prefix (e.g., "EQUIPMENT_")
     * @param level        optional comma-separated log levels
     * @param search       optional text search across details/action/actor/entity/traceId
     * @param actorId      optional exact actor id
     * @param from,to      optional ISO-8601 instants bounding the time window
     */
    @GetMapping
    public ResponseEntity<Page<Log>> getLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String actionPrefix,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        List<String> categories = splitParam(category);
        List<String> levels = splitParam(level);
        if (levels.isEmpty()) {
            levels = List.of("INFO", "WARN", "ERROR", "FATAL");
        }

        Page<Log> logs = logQueryService.search(
                categories, actionPrefix, levels, search, actorId, from, to,
                PageRequest.of(safePage, safeSize));

        log.debug("Fetched {} logs, page {}", logs.getNumberOfElements(), safePage);
        return ResponseEntity.ok(logs);
    }

    /**
     * Aggregated statistics for the admin logs dashboard.
     *
     * @param hours lookback window in hours (default 24, capped at 30 days)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String category) {
        int safeHours = Math.min(Math.max(hours, 1), MAX_STATS_WINDOW_HOURS);
        return ResponseEntity.ok(logQueryService.stats(safeHours, splitParam(category)));
    }

    private List<String> splitParam(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .toList();
    }
}
