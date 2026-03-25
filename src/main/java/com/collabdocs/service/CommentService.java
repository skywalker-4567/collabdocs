package com.collabdocs.service;

import com.collabdocs.dto.request.CommentRequest;
import com.collabdocs.dto.response.AuthResponse;
import com.collabdocs.dto.response.CommentResponse;
import com.collabdocs.entity.Comment;
import com.collabdocs.entity.CommentThread;
import com.collabdocs.entity.Document;
import com.collabdocs.entity.User;
import com.collabdocs.exception.AccessDeniedException;
import com.collabdocs.exception.ResourceNotFoundException;
import com.collabdocs.repository.CommentRepository;
import com.collabdocs.repository.CommentThreadRepository;
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
public class CommentService {

    private final CommentThreadRepository threadRepository;
    private final CommentRepository commentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final SecurityUtils securityUtils;

    // -------------------------------------------------------------------------
    // Threads
    // -------------------------------------------------------------------------

    /**
     * Creates a thread anchored to [startIndex, endIndex) in the document.
     * The selected text is snapshotted as anchoredText for display purposes.
     * The first comment body is required — a thread without a comment makes no sense.
     * Any EDITOR or OWNER can create threads. VIEWERs cannot.
     */
    @Transactional
    public CommentResponse.ThreadDetail createThread(
            Long documentId, CommentRequest.CreateThread request) {

        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        var role = documentService.resolveRoleOrThrow(document, currentUser);
        if (role == com.collabdocs.enums.Role.VIEWER) {
            throw new AccessDeniedException("VIEWERs cannot create comment threads");
        }

        // Snapshot the selected text at creation time
        String anchoredText = extractRange(
                document.getContent(),
                request.startIndex(),
                request.endIndex()
        );

        CommentThread thread = CommentThread.builder()
                .document(document)
                .createdBy(currentUser)
                .startIndex(request.startIndex())
                .endIndex(request.endIndex())
                .anchoredText(anchoredText)
                .resolved(false)
                .build();

        threadRepository.save(thread);

        // First comment — always created with the thread
        Comment firstComment = Comment.builder()
                .thread(thread)
                .user(currentUser)
                .body(request.body())
                .build();

        commentRepository.save(firstComment);
        thread.getComments().add(firstComment);

        log.info("Thread {} created on document {} by {}", thread.getId(), documentId, currentUser.getEmail());
        return toThreadDetail(thread);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse.ThreadDetail> getThreads(Long documentId, Boolean resolved) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);
        documentService.resolveRoleOrThrow(document, currentUser); // any role can view

        List<CommentThread> threads = resolved == null
                ? threadRepository.findByDocumentOrderByCreatedAtAsc(document)
                : threadRepository.findByDocumentAndResolvedOrderByCreatedAtAsc(document, resolved);

        return threads.stream().map(this::toThreadDetail).toList();
    }

    /**
     * Resolves (closes) a thread. Only the thread creator or the document OWNER
     * can resolve a thread.
     */
    @Transactional
    public CommentResponse.ThreadDetail resolveThread(Long documentId, Long threadId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);
        documentService.resolveRoleOrThrow(document, currentUser);

        CommentThread thread = findThreadOrThrow(threadId, documentId);

        boolean isOwner = documentService.resolveRoleOrThrow(document, currentUser)
                == com.collabdocs.enums.Role.OWNER;
        boolean isThreadCreator = thread.getCreatedBy().getId().equals(currentUser.getId());

        if (!isOwner && !isThreadCreator) {
            throw new AccessDeniedException("Only the thread creator or document OWNER can resolve threads");
        }

        thread.setResolved(true);
        threadRepository.save(thread);

        log.info("Thread {} resolved by {}", threadId, currentUser.getEmail());
        return toThreadDetail(thread);
    }

    @Transactional
    public void deleteThread(Long documentId, Long threadId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        var role = documentService.resolveRoleOrThrow(document, currentUser);
        CommentThread thread = findThreadOrThrow(threadId, documentId);

        boolean isOwner = role == com.collabdocs.enums.Role.OWNER;
        boolean isThreadCreator = thread.getCreatedBy().getId().equals(currentUser.getId());

        if (!isOwner && !isThreadCreator) {
            throw new AccessDeniedException("Only the thread creator or document OWNER can delete threads");
        }

        threadRepository.delete(thread);
        log.info("Thread {} deleted by {}", threadId, currentUser.getEmail());
    }

    // -------------------------------------------------------------------------
    // Comments (replies)
    // -------------------------------------------------------------------------

    /**
     * Adds a reply to an existing thread.
     * Any EDITOR or OWNER can reply. VIEWERs cannot.
     */
    @Transactional
    public CommentResponse.ThreadDetail addComment(
            Long documentId, Long threadId, CommentRequest.AddComment request) {

        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        var role = documentService.resolveRoleOrThrow(document, currentUser);
        if (role == com.collabdocs.enums.Role.VIEWER) {
            throw new AccessDeniedException("VIEWERs cannot reply to threads");
        }

        CommentThread thread = findThreadOrThrow(threadId, documentId);

        if (thread.isResolved()) {
            throw new AccessDeniedException("Cannot reply to a resolved thread");
        }

        Comment comment = Comment.builder()
                .thread(thread)
                .user(currentUser)
                .body(request.body())
                .build();

        commentRepository.save(comment);
        thread.getComments().add(comment);

        return toThreadDetail(thread);
    }

    /**
     * Updates the body of an existing comment.
     * Only the comment author can edit their own comment.
     */
    @Transactional
    public CommentResponse.CommentDetail updateComment(
            Long documentId, Long threadId, Long commentId,
            CommentRequest.UpdateComment request) {

        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);
        documentService.resolveRoleOrThrow(document, currentUser);

        findThreadOrThrow(threadId, documentId); // validate thread belongs to document

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!comment.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only edit your own comments");
        }

        comment.setBody(request.body());
        commentRepository.save(comment);

        return toCommentDetail(comment);
    }

    /**
     * Deletes a comment. Author or document OWNER can delete.
     * Cannot delete the first comment in a thread (delete the thread instead).
     */
    @Transactional
    public void deleteComment(Long documentId, Long threadId, Long commentId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        var role = documentService.resolveRoleOrThrow(document, currentUser);
        CommentThread thread = findThreadOrThrow(threadId, documentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        // Prevent deleting the first comment — delete the thread instead
        List<Comment> allComments = commentRepository.findByThreadOrderByCreatedAtAsc(thread);
        if (!allComments.isEmpty() && allComments.get(0).getId().equals(commentId)) {
            throw new AccessDeniedException(
                    "Cannot delete the first comment in a thread. Delete the thread instead.");
        }

        boolean isOwner = role == com.collabdocs.enums.Role.OWNER;
        boolean isAuthor = comment.getUser().getId().equals(currentUser.getId());

        if (!isOwner && !isAuthor) {
            throw new AccessDeniedException("You can only delete your own comments");
        }

        commentRepository.delete(comment);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private CommentThread findThreadOrThrow(Long threadId, Long documentId) {
        CommentThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Thread not found: " + threadId));

        if (!thread.getDocument().getId().equals(documentId)) {
            throw new ResourceNotFoundException("Thread " + threadId + " does not belong to document " + documentId);
        }

        return thread;
    }

    /**
     * Extracts the substring [start, end) from content safely.
     * If indices are out of bounds, returns whatever is available.
     */
    private String extractRange(String content, int start, int end) {
        if (content == null || content.isEmpty()) return "";
        int safeStart = Math.max(0, Math.min(start, content.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, content.length()));
        return content.substring(safeStart, safeEnd);
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private CommentResponse.ThreadDetail toThreadDetail(CommentThread thread) {
        List<Comment> comments = commentRepository.findByThreadOrderByCreatedAtAsc(thread);

        return new CommentResponse.ThreadDetail(
                thread.getId(),
                thread.getDocument().getId(),
                thread.getStartIndex(),
                thread.getEndIndex(),
                thread.getAnchoredText(),
                thread.isResolved(),
                toUserSummary(thread.getCreatedBy()),
                comments.stream().map(this::toCommentDetail).toList(),
                thread.getCreatedAt()
        );
    }

    private CommentResponse.CommentDetail toCommentDetail(Comment comment) {
        return new CommentResponse.CommentDetail(
                comment.getId(),
                comment.getThread().getId(),
                toUserSummary(comment.getUser()),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private AuthResponse.UserSummary toUserSummary(User user) {
        return new AuthResponse.UserSummary(user.getId(), user.getEmail(), user.getFullName());
    }
}