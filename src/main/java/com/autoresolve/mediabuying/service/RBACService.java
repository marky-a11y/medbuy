package com.autoresolve.mediabuying.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RBACService {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Checks if the currently authenticated user has any of the specified roles.
     */
    public boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return Arrays.stream(roles)
                .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role)
                .anyMatch(userRoles::contains);
    }

    /**
     * Checks if the current user has the MEDIA_ANALYST role.
     */
    @PreAuthorize("hasRole('MEDIA_ANALYST')")
    public boolean isMediaAnalyst() {
        return hasAnyRole("MEDIA_ANALYST", "ADMIN");
    }

    /**
     * Checks if the current user has the ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public boolean isAdmin() {
        return hasAnyRole("ADMIN");
    }

    /**
     * Returns the current user's username.
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        return auth.getName();
    }
}
