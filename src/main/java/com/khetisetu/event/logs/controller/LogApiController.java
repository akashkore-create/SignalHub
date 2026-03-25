package com.khetisetu.event.logs.controller;

import com.khetisetu.event.logs.repository.LogRepository;
import com.khetisetu.event.notifications.model.logs.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private final LogRepository logRepository;

    /**
     * Gets paginated logs, filterable by action prefix, level, and search term.
     *
     * @param actionPrefix optional action prefix filter (e.g., "EQUIPMENT_", "ORDER_")
     * @param level        optional log level filter (INFO, WARN, ERROR)
     * @param search       optional text search in log details
     * @param page         page number (0-indexed)
     * @param size         page size
     */
    @GetMapping
    public ResponseEntity<Page<Log>> getLogs(
            @RequestParam(required = false) String actionPrefix,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("timestamp")));

        String actionRegex = actionPrefix != null ? actionPrefix : ".*";
        String[] levels = level != null ? new String[]{level} : new String[]{"INFO", "WARN", "ERROR"};

        Page<Log> logs;
        if (search != null && !search.isEmpty()) {
            logs = logRepository.findByDetailsRegexAndActionRegexAndLevelIn(search, actionRegex, levels, pageable);
        } else {
            logs = logRepository.findByActionRegexAndLevelIn(actionRegex, levels, pageable);
        }

        log.debug("Fetched {} logs, page {}", logs.getNumberOfElements(), page);
        return ResponseEntity.ok(logs);
    }
}
