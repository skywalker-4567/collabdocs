package com.collabdocs.repository;

import com.collabdocs.entity.Comment;
import com.collabdocs.entity.CommentThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByThreadOrderByCreatedAtAsc(CommentThread thread);
}
