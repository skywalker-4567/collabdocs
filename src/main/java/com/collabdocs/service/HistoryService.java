package com.collabdocs.service;

import com.collabdocs.dto.response.AuthResponse;
import com.collabdocs.dto.response.HistoryResponse;
import com.collabdocs.entity.Document;
import com.collabdocs.entity.DocumentOperation;
import com.collabdocs.entity.User;
import com.collabdocs.enums.OperationType;
import com.collabdocs.exception.AccessDeniedException;
import com.collabdocs.exception.ResourceNotFoundException;
import com.collabdocs.repository.DocumentOperationRepository;
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
public class HistoryService {

    private final DocumentRepository documentRepository;
    private final DocumentOperationRepository operationRepository;
    private final DocumentService documentService;
    private final SecurityUtils securityUtils;

    // -------------------------------------------------------------------------
    // Get full operation history for a document
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HistoryResponse.FullHistory getHistory(Long documentId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        // Any role can view history
        documentService.resolveRoleOrThrow(document, currentUser);

        List<DocumentOperation> operations =
                operationRepository.findByDocumentOrderByServerVersionAsc(document);

        List<HistoryResponse.OperationEntry> entries = operations.stream()
                .map(op -> new HistoryResponse.OperationEntry(
                        op.getId(),
                        op.getType(),
                        op.getPosition(),
                        op.getContent(),
                        op.getLength(),
                        op.getServerVersion(),
                        toUserSummary(op.getUser()),
                        op.getCreatedAt()
                ))
                .toList();

        return new HistoryResponse.FullHistory(
                document.getId(),
                document.getTitle(),
                document.getVersion(),
                entries
        );
    }

    // -------------------------------------------------------------------------
    // Restore to a specific version
    // -------------------------------------------------------------------------

    /**
     * Reconstructs the document content at a given version by replaying
     * all operations from the beginning up to and including targetVersion.
     *
     * Then saves that content as the current document state and records
     * a new operation in the log marking the restore.
     *
     * Only OWNER can restore.
     */
    @Transactional
    public HistoryResponse.FullHistory restoreToVersion(Long documentId, Long targetVersion) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);
        String oldContent = document.getContent() != null ? document.getContent() : "";
        int oldContentLength = oldContent.length();

        // Only OWNER can restore
        var role = documentService.resolveRoleOrThrow(document, currentUser);
        if (role != com.collabdocs.enums.Role.OWNER) {
            throw new AccessDeniedException("Only the OWNER can restore a document version");
        }

        if (targetVersion < 0 || targetVersion > document.getVersion()) {
            throw new IllegalArgumentException(
                    "Invalid targetVersion: " + targetVersion +
                            ". Current version is " + document.getVersion()
            );
        }

        // Fetch all operations up to and including the target version
        List<DocumentOperation> operations =
                operationRepository.findByDocumentOrderByServerVersionAsc(document)
                        .stream()
                        .filter(op -> op.getServerVersion() <= targetVersion)
                        .toList();

        // Replay from scratch to reconstruct content at targetVersion
        String reconstructed = replayOperations(operations);

        // Save restored content — bump version so clients re-sync
        long newVersion = document.getVersion() + 1;
        document.setContent(reconstructed);
        document.setVersion(newVersion);
        documentRepository.save(document);

        // Record the restore itself as an operation in the log
        // REPLACE from position 0 covering the entire old content
        DocumentOperation restoreOp = DocumentOperation.builder()
                .document(document)
                .user(currentUser)
                .type(OperationType.REPLACE)
                .position(0)
                .content(reconstructed)
                .length(oldContentLength)   // ← fixed
                .clientVersion(document.getVersion() - 1)
                .serverVersion(newVersion)
                .build();
        operationRepository.save(restoreOp);

        log.info("Document {} restored to version {} by {}. New version: {}",
                documentId, targetVersion, currentUser.getEmail(), newVersion);

        return getHistory(documentId);
    }

    // -------------------------------------------------------------------------
    // Preview — reconstruct content at a version without saving
    // -------------------------------------------------------------------------

    /**
     * Returns what the document content looked like at a given version.
     * Does NOT modify the document. Any role can preview.
     */
    @Transactional(readOnly = true)
    public String previewAtVersion(Long documentId, Long targetVersion) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        documentService.resolveRoleOrThrow(document, currentUser);

        if (targetVersion < 0 || targetVersion > document.getVersion()) {
            throw new IllegalArgumentException("Invalid targetVersion: " + targetVersion);
        }

        List<DocumentOperation> operations =
                operationRepository.findByDocumentOrderByServerVersionAsc(document)
                        .stream()
                        .filter(op -> op.getServerVersion() <= targetVersion)
                        .toList();

        return replayOperations(operations);
    }

    // -------------------------------------------------------------------------
    // Core replay engine
    // -------------------------------------------------------------------------

    /**
     * Replays a list of operations in order on an empty string.
     * This is the same logic as DocumentEditService.applyOperation —
     * kept separate so history replay never triggers Redis or DB side effects.
     */
    private String replayOperations(List<DocumentOperation> operations) {
        String content = "";

        for (DocumentOperation op : operations) {
            content = applyOperation(content, op);
        }

        return content;
    }

    private String applyOperation(String content, DocumentOperation op) {
        int pos = Math.min(op.getPosition(), content.length());

        return switch (op.getType()) {
            case INSERT -> {
                String text = op.getContent() != null ? op.getContent() : "";
                yield content.substring(0, pos) + text + content.substring(pos);
            }
            case DELETE -> {
                int end = Math.min(pos + op.getLength(), content.length());
                yield content.substring(0, pos) + content.substring(end);
            }
            case REPLACE -> {
                int end = Math.min(pos + op.getLength(), content.length());
                String text = op.getContent() != null ? op.getContent() : "";
                yield content.substring(0, pos) + text + content.substring(end);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private AuthResponse.UserSummary toUserSummary(User user) {
        return new AuthResponse.UserSummary(user.getId(), user.getEmail(), user.getFullName());
    }
}