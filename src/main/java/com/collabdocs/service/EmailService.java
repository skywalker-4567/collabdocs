package com.collabdocs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Sends a share notification email asynchronously so it never blocks
     * the HTTP response.
     *
     * @param toEmail       recipient's email
     * @param sharedByName  display name of the user who shared
     * @param documentTitle title of the document shared
     * @param role          role granted (VIEWER / EDITOR)
     */
    @Async
    public void sendShareNotification(
            String toEmail,
            String sharedByName,
            String documentTitle,
            String role
    ) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Document shared with you: " + documentTitle);
            message.setText(
                    "Hi,\n\n" +
                    sharedByName + " has shared the document \"" + documentTitle +
                    "\" with you as " + role + ".\n\n" +
                    "Log in to CollabDocs to access it.\n\n" +
                    "— The CollabDocs Team"
            );
            mailSender.send(message);
            log.info("Share notification sent to {}", toEmail);
        } catch (Exception e) {
            // Email failure should never break the share operation
            log.error("Failed to send share notification to {}: {}", toEmail, e.getMessage());
        }
    }
}
