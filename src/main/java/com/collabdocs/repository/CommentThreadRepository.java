package com.collabdocs.repository;

import com.collabdocs.entity.CommentThread;
import com.collabdocs.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentThreadRepository extends JpaRepository<CommentThread, Long> {

    List<CommentThread> findByDocumentOrderByCreatedAtAsc(Document document);

    List<CommentThread> findByDocumentAndResolvedOrderByCreatedAtAsc(Document document, boolean resolved);
}
