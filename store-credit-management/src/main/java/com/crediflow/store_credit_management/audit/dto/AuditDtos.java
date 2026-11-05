package com.crediflow.store_credit_management.audit.dto;

import com.crediflow.store_credit_management.audit.domain.model.AuditAction;
import com.crediflow.store_credit_management.audit.domain.model.AuditEvent;

import java.time.LocalDateTime;

/**
 * Audit DTOs
 * @summary
 * Salida de la consulta de auditoría.
 *
 * @since 1.0.0
 */
public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEventResource(
            Long id,
            LocalDateTime occurredAt,
            Long userId,
            String username,
            Long storeId,
            AuditAction action,
            String entityType,
            Long entityId,
            String detail,
            String ip
    ) {
        public static AuditEventResource from(AuditEvent event) {
            return new AuditEventResource(
                    event.getId(),
                    event.getOccurredAt(),
                    event.getUserId(),
                    event.getUsername(),
                    event.getStoreId(),
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getDetail(),
                    event.getIp());
        }
    }
}
