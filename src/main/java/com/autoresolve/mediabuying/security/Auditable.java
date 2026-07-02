package com.autoresolve.mediabuying.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be audited.
 * Used by AuditLoggingAspect to log method executions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Optional description of the action being audited.
     */
    String action() default "";

    /**
     * The entity type being acted upon.
     */
    String entityType() default "";
}
