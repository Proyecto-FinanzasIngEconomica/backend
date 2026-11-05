package com.crediflow.store_credit_management.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Audit Event
 * @summary
 * Un renglón del registro de auditoría: quién (usuario), qué (acción y
 * entidad), cuándo, desde dónde (IP) y un detalle breve en texto.
 *
 * Solo se inserta: es @Immutable (Hibernate ignora cualquier modificación), no
 * tiene métodos que cambien nada y no hay servicio de borrado ni edición.
 * Guarda el nombre de usuario además del id, para que el registro siga siendo
 * legible aunque la cuenta cambie.
 *
 * @since 1.0.0
 */
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_store_time", columnList = "storeId, occurredAt"),
        @Index(name = "idx_audit_user", columnList = "userId")
})
public class AuditEvent {

    private static final int USERNAME_MAX = 50;
    private static final int DETAIL_MAX = 500;
    private static final int IP_MAX = 45;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** Quién actuó. Nulo si no hay usuario identificado (login con usuario inexistente). */
    @Column(updatable = false)
    private Long userId;

    @Column(updatable = false, length = USERNAME_MAX)
    private String username;

    /** Tienda a la que pertenece el evento. Nula para el administrador del sistema. */
    @Column(updatable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private AuditAction action;

    @Column(updatable = false, length = 30)
    private String entityType;

    @Column(updatable = false)
    private Long entityId;

    @Column(updatable = false, length = DETAIL_MAX)
    private String detail;

    @Column(updatable = false, length = IP_MAX)
    private String ip;

    public static AuditEvent of(AuditAction action, Long userId, String username, Long storeId,
                                String entityType, Long entityId, String detail, String ip) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de auditoría es obligatoria");
        }
        AuditEvent event = new AuditEvent();
        event.occurredAt = LocalDateTime.now();
        event.action = action;
        event.userId = userId;
        event.username = truncate(username, USERNAME_MAX);
        event.storeId = storeId;
        event.entityType = truncate(entityType, 30);
        event.entityId = entityId;
        event.detail = truncate(detail, DETAIL_MAX);
        event.ip = truncate(ip, IP_MAX);
        return event;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
