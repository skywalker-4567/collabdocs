package com.collabdocs.repository;

import com.collabdocs.entity.Document;
import com.collabdocs.entity.DocumentPermission;
import com.collabdocs.entity.User;
import com.collabdocs.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    Optional<DocumentPermission> findByDocumentAndUser(Document document, User user);

    List<DocumentPermission> findByDocument(Document document);

    boolean existsByDocumentAndUserAndRoleIn(Document document, User user, List<Role> roles);

    void deleteByDocumentAndUser(Document document, User user);
}
