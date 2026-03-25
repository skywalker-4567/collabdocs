package com.collabdocs.repository;

import com.collabdocs.entity.Document;
import com.collabdocs.entity.DocumentOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentOperationRepository extends JpaRepository<DocumentOperation, Long> {

    List<DocumentOperation> findByDocumentOrderByServerVersionAsc(Document document);

    /**
     * Returns all operations after a given server version.
     * Used for incremental history fetch or partial restore.
     */
    List<DocumentOperation> findByDocumentAndServerVersionGreaterThanOrderByServerVersionAsc(
            Document document, Long serverVersion);
}
