package com.collabdocs.service;

import com.collabdocs.dto.request.DocumentRequest;
import com.collabdocs.dto.response.DocumentResponse;
import com.collabdocs.entity.Document;
import com.collabdocs.entity.DocumentPermission;
import com.collabdocs.entity.User;
import com.collabdocs.enums.Role;
import com.collabdocs.exception.AccessDeniedException;
import com.collabdocs.exception.ConflictException;
import com.collabdocs.exception.ResourceNotFoundException;
import com.collabdocs.repository.DocumentPermissionRepository;
import com.collabdocs.repository.DocumentRepository;
import com.collabdocs.repository.UserRepository;
import com.collabdocs.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecurityUtils securityUtils;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Transactional
    public DocumentResponse.Detail createDocument(DocumentRequest.Create request) {
        User owner = securityUtils.getCurrentUser();

        Document document = Document.builder()
                .title(request.title())
                .content(request.content() != null ? request.content() : "")
                .owner(owner)
                .build();

        documentRepository.save(document);

        // Owner always gets an OWNER permission entry for uniform permission queries
        DocumentPermission ownerPermission = DocumentPermission.builder()
                .document(document)
                .user(owner)
                .role(Role.OWNER)
                .build();

        permissionRepository.save(ownerPermission);

        log.info("Document {} created by user {}", document.getId(), owner.getEmail());
        return toDetail(document, Role.OWNER);
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DocumentResponse.Detail getDocument(Long documentId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);
        Role role = resolveRoleOrThrow(document, currentUser);
        return toDetail(document, role);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse.Summary> getMyDocuments(Pageable pageable) {
        User currentUser = securityUtils.getCurrentUser();
        return documentRepository.findAllAccessibleByUser(currentUser, pageable)
                .map(this::toSummary);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Transactional
    public DocumentResponse.Detail updateDocument(Long documentId, DocumentRequest.Update request) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        Role role = resolveRoleOrThrow(document, currentUser);
        requireAtLeast(role, Role.EDITOR, "Only EDITORs and OWNERs can update documents");

        if (request.title() != null && !request.title().isBlank()) {
            document.setTitle(request.title());
        }
        if (request.content() != null) {
            document.setContent(request.content());
        }

        documentRepository.save(document);
        return toDetail(document, role);
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Transactional
    public void deleteDocument(Long documentId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        Role role = resolveRoleOrThrow(document, currentUser);
        requireAtLeast(role, Role.OWNER, "Only the OWNER can delete a document");

        documentRepository.delete(document);
        log.info("Document {} deleted by user {}", documentId, currentUser.getEmail());
    }

    // -------------------------------------------------------------------------
    // PERMISSIONS — Share
    // -------------------------------------------------------------------------

    @Transactional
    public DocumentResponse.Detail shareDocument(Long documentId, DocumentRequest.Share request) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        Role callerRole = resolveRoleOrThrow(document, currentUser);
        requireAtLeast(callerRole, Role.OWNER, "Only the OWNER can share documents");

        if (request.role() == Role.OWNER) {
            throw new ConflictException("Cannot assign OWNER role via share. Transfer ownership separately.");
        }

        User target = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email: " + request.email()));

        if (target.getId().equals(currentUser.getId())) {
            throw new ConflictException("You cannot share a document with yourself");
        }

        // Upsert: update role if permission already exists
        DocumentPermission permission = permissionRepository
                .findByDocumentAndUser(document, target)
                .map(existing -> {
                    existing.setRole(request.role());
                    return existing;
                })
                .orElse(DocumentPermission.builder()
                        .document(document)
                        .user(target)
                        .role(request.role())
                        .build());

        permissionRepository.save(permission);

        emailService.sendShareNotification(
                target.getEmail(),
                currentUser.getFullName(),
                document.getTitle(),
                request.role().name()
        );

        log.info("Document {} shared with {} as {}", documentId, target.getEmail(), request.role());
        return toDetail(document, callerRole);
    }

    // -------------------------------------------------------------------------
    // PERMISSIONS — Revoke
    // -------------------------------------------------------------------------

    @Transactional
    public void revokeAccess(Long documentId, Long targetUserId) {
        User currentUser = securityUtils.getCurrentUser();
        Document document = findDocumentOrThrow(documentId);

        Role callerRole = resolveRoleOrThrow(document, currentUser);
        requireAtLeast(callerRole, Role.OWNER, "Only the OWNER can revoke access");

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        if (target.getId().equals(document.getOwner().getId())) {
            throw new ConflictException("Cannot revoke the owner's access");
        }

        permissionRepository.deleteByDocumentAndUser(document, target);
        log.info("Access to document {} revoked for user {}", documentId, targetUserId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    /**
     * Resolves the caller's Role for a document.
     * Throws AccessDeniedException if they have no permission entry at all.
     */
    public Role resolveRoleOrThrow(Document document, User user) {
        return permissionRepository.findByDocumentAndUser(document, user)
                .map(DocumentPermission::getRole)
                .orElseThrow(() -> new AccessDeniedException("You do not have access to this document"));
    }

    /**
     * Enforces a minimum role level.
     * Role hierarchy: VIEWER < EDITOR < OWNER (by ordinal).
     */
    private void requireAtLeast(Role actual, Role required, String message) {
        if (actual.ordinal() < required.ordinal()) {
            throw new AccessDeniedException(message);
        }
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private DocumentResponse.Summary toSummary(Document doc) {
        return new DocumentResponse.Summary(
                doc.getId(),
                doc.getTitle(),
                doc.getVersion(),
                doc.getOwner().getEmail(),
                doc.getUpdatedAt()
        );
    }

    public DocumentResponse.Detail toDetail(Document doc, Role yourRole) {
        List<DocumentResponse.PermissionEntry> permissions =
                permissionRepository.findByDocument(doc).stream()
                        .map(p -> new DocumentResponse.PermissionEntry(
                                p.getUser().getId(),
                                p.getUser().getEmail(),
                                p.getUser().getFullName(),
                                p.getRole(),
                                p.getGrantedAt()
                        ))
                        .toList();

        return new DocumentResponse.Detail(
                doc.getId(),
                doc.getTitle(),
                doc.getContent(),
                doc.getVersion(),
                doc.getOwner().getEmail(),
                yourRole,
                permissions,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
