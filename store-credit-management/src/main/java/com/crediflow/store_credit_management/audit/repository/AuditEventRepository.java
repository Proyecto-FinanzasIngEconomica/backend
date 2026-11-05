package com.crediflow.store_credit_management.audit.repository;

import com.crediflow.store_credit_management.audit.domain.model.AuditAction;
import com.crediflow.store_credit_management.audit.domain.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Audit Event Repository
 * @summary
 * Consulta del registro con filtros opcionales. Cada filtro nulo se ignora.
 * El rango de fechas es [from, to): "to" es exclusivo.
 *
 * @since 1.0.0
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
            select e from AuditEvent e
            where (:storeId is null or e.storeId = :storeId)
              and (:action is null or e.action = :action)
              and (:userId is null or e.userId = :userId)
              and (:from is null or e.occurredAt >= :from)
              and (:to is null or e.occurredAt < :to)
            """)
    Page<AuditEvent> search(@Param("storeId") Long storeId,
                            @Param("action") AuditAction action,
                            @Param("userId") Long userId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            Pageable pageable);
}