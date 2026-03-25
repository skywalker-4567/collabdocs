package com.collabdocs.entity;

import com.collabdocs.enums.OperationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only log of every accepted edit to a document.
 * Used for full version history and point-in-time restore.
 *
 * Fields:
 *  - position: character index where the operation starts
 *  - content:  inserted/replaced text (null for DELETE)
 *  - length:   number of characters deleted/replaced (0 for INSERT)
 *  - clientVersion: the version the client had when they sent this edit
 *  - serverVersion: the version after this operation was applied
 */
@Entity
@Table(name = "document_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationType type;

    @Column(nullable = false)
    private Integer position;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer length;

    @Column(nullable = false)
    private Long clientVersion;

    @Column(nullable = false)
    private Long serverVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
