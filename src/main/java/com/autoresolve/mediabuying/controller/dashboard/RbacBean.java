package com.autoresolve.mediabuying.controller.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Request-scoped bean for RBAC checks in JSF pages.
 * Provides EL-friendly methods for conditional rendering of KPI values.
 */
@Component
@Scope("request")
public class RbacBean implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(RbacBean.class);
    private static final long serialVersionUID = 1L;

    /**
     * Returns true if the current user has MEDIA_ANALYST or ADMIN role.
     */
    public boolean isMediaAnalyst() {
        return hasAnyRole("ROLE_MEDIA_ANALYST", "ROLE_ADMIN");
    }

    /**
     * Returns true if the current user has ADMIN role.
     */
    public boolean isAdmin() {
        return hasAnyRole("ROLE_ADMIN");
    }

    /**
     * Returns true if the current user can view full KPI values (i.e., not VIEWER-only).
     * Used from JSF EL as #{rbacBean.canViewFullKpis} (requires is-prefix for property access).
     */
    public boolean isCanViewFullKpis() {
        return canViewFullKpis();
    }

    /**
     * Returns true if the current user can view full KPI values (i.e., not VIEWER-only).
     */
    public boolean canViewFullKpis() {
        return isMediaAnalyst();
    }

    /**
     * Returns the current user's role name for display purposes.
     */
    public String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "VIEWER";
        }
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .orElse("VIEWER");
    }

    private boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            for (String role : roles) {
                if (authority.getAuthority().equals(role)) {
                    return true;
                }
            }
        }
        return false;
    }
}
