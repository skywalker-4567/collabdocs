package com.collabdocs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A comment thread anchored to a character range [startIndex, endIndex) in the document.
 * The range is stored at creation time and is NOT automatically updated as the document changes.
 * Future phases may add range tracking via operations log if needed.
 */
@Entity
@Table(name = "comment_threads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private Integer startIndex;

    @Column(nullable = false)
    private Integer endIndex;

    /**
     * The exact text selected when the thread was created.
     * Stored for display purposes even if the document content later changes.
     */
    @Column(columnDefinition = "TEXT")
    private String anchoredText;

    @Column(nullable = false)
    private boolean resolved;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.resolved = false;
    }
}
