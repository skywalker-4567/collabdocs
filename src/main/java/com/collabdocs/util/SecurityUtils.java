package com.collabdocs.util;

import com.collabdocs.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    /**
     * Returns the currently authenticated User from the SecurityContext.
     * Safe to call in any @Service or @RestController after JwtAuthFilter runs.
     */
    public User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
