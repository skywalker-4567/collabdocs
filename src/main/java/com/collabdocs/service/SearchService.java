package com.collabdocs.service;

import com.collabdocs.dto.response.DocumentResponse;
import com.collabdocs.entity.Document;
import com.collabdocs.entity.User;
import com.collabdocs.exception.AccessDeniedException;
import com.collabdocs.repository.DocumentPermissionRepository;
import com.collabdocs.repository.DocumentRepository;
import com.collabdocs.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final DocumentService documentService;
    private final SecurityUtils securityUtils;

    /**
     * Searches documents by full-text query, then filters to only those
     * the current user has permission to access.
     *
     * Two-step approach:
     *  1. PostgreSQL tsvector search returns ranked results
     *  2. Java-side permission filter removes documents the user can't see
     *
     * This is simpler than embedding permission logic in the native query,
     * and correct for a portfolio-scale project. At large scale you'd push
     * the permission join into SQL.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse.Summary> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        User currentUser = securityUtils.getCurrentUser();

        List<Document> results = documentRepository.fullTextSearch(query.trim());

        // Filter to only documents the current user can access
        return results.stream()
                .filter(doc -> hasAccess(doc, currentUser))
                .map(doc -> new DocumentResponse.Summary(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getVersion(),
                        doc.getOwner().getEmail(),
                        doc.getUpdatedAt()
                ))
                .toList();
    }

    private boolean hasAccess(Document document, User user) {
        return permissionRepository.findByDocumentAndUser(document, user).isPresent();
    }
}