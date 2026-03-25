package com.collabdocs.repository;

import com.collabdocs.entity.Document;
import com.collabdocs.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByOwner(User owner, Pageable pageable);

    /**
     * Returns all documents the user has any permission on
     * (either owns or was explicitly shared with).
     */
    @Query("""
        SELECT DISTINCT d FROM Document d
        JOIN DocumentPermission dp ON dp.document = d
        WHERE dp.user = :user
        ORDER BY d.updatedAt DESC
        """)
    Page<Document> findAllAccessibleByUser(@Param("user") User user, Pageable pageable);

    /**
     * Full-text search using PostgreSQL tsvector.
     * Weighted: title rank (A) > content rank (B).
     * Populated by a DB trigger on insert/update.
     */
    @Query(value = """
        SELECT * FROM documents
        WHERE search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
        """, nativeQuery = true)
    List<Document> fullTextSearch(@Param("query") String query);
}
