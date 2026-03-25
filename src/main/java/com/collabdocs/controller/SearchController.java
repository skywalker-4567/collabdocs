package com.collabdocs.controller;

import com.collabdocs.dto.response.DocumentResponse;
import com.collabdocs.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * GET /api/search?q=your+query
     *
     * Returns documents matching the query that the current user has access to.
     * Results are ranked by PostgreSQL ts_rank — title matches rank higher than
     * content matches because title has weight 'A', content has weight 'B'.
     *
     * Examples:
     *   /api/search?q=sprint+planning
     *   /api/search?q=hello+world
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse.Summary>> search(
            @RequestParam(name = "q") String query) {
        return ResponseEntity.ok(searchService.search(query));
    }
}