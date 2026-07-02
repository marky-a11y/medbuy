package com.autoresolve.mediabuying.security;

import com.autoresolve.mediabuying.model.entity.AuditLog;
import com.autoresolve.mediabuying.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
public class AuditLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingAspect.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLoggingAspect(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable) && execution(* com.autoresolve.mediabuying.service..*(..))")
    public Object logAudit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String action = auditable.action().isEmpty() ?
                joinPoint.getSignature().getName() : auditable.action();
        String entityType = auditable.entityType().isEmpty() ?
                joinPoint.getTarget().getClass().getSimpleName() : auditable.entityType();

        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }

        String username = SecurityContextHolder.getContext().getAuthentication() != null ?
                SecurityContextHolder.getContext().getAuthentication().getName() : "anonymous";

        long startTime = System.currentTimeMillis();
        Object result;

        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            // Log the failure
            log.warn("AUDIT FAILURE | user={} | action={} | entity={} | correlationId={} | error={}",
                    username, action, entityType, correlationId, e.getMessage());
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;

        // Build audit record
        AuditLog audit = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .details(buildDetails(joinPoint, result).toString())
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

        // Save asynchronously to avoid blocking the main flow
        try {
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.warn("Failed to persist audit log: {}", e.getMessage());
        }

        log.info("AUDIT | user={} | action={} | entity={} | duration={}ms | correlationId={}",
                username, action, entityType, duration, correlationId);

        return result;
    }

    private JsonNode buildDetails(ProceedingJoinPoint joinPoint, Object result) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("method", joinPoint.getSignature().toShortString());
        details.put("args", Arrays.toString(joinPoint.getArgs()));
        details.put("resultType", result != null ? result.getClass().getSimpleName() : "void");
        return details;
    }
}
