package com.crediflow.store_credit_management.audit.service;

import com.crediflow.store_credit_management.audit.domain.model.AuditAction;
import com.crediflow.store_credit_management.audit.domain.model.AuditEvent;
import com.crediflow.store_credit_management.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit Service
 * @summary
 * Registra y consulta eventos de auditoría.
 *
 * record(...) se une a la transacción de quien lo llama: el evento se guarda
 * o se descarta junto con la operación (una compra sin registro, o un registro
 * de una compra que no ocurrió, son imposibles). El usuario sale del token de
 * la petición en curso; la IP, de la conexión.
 *
 * No depende de ningún otro módulo: todos los demás dependen de este.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;

    /** Registra una acción hecha por el usuario autenticado de la petición actual. */
    @Transactional
    public void record(AuditAction action, Long storeId, String entityType, Long entityId, String detail) {
        Long userId = null;
        String username = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            userId = parseId(token.getToken().getSubject());
            username = token.getToken().getClaimAsString("username");
        }
        save(action, userId, username, storeId, entityType, entityId, detail);
    }

    /** Registra una acción de un usuario que aún no tiene sesión (el login). */
    @Transactional
    public void recordAs(AuditAction action, Long userId, String username, Long storeId, String detail) {
        save(action, userId, username, storeId, null, null, detail);
    }

    /** Consulta paginada. "to" es inclusiva a nivel de día. Los filtros nulos se ignoran. */
    @Transactional(readOnly = true)
    public Page<AuditEvent> search(Long storeId, AuditAction action, Long userId,
                                   LocalDate from, LocalDate to, Pageable pageable) {
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
        return repository.search(storeId, action, userId, fromTime, toTime, pageable);
    }

    private void save(AuditAction action, Long userId, String username, Long storeId,
                      String entityType, Long entityId, String detail) {
        repository.save(AuditEvent.of(
                action, userId, username, storeId, entityType, entityId, detail, currentIp()));
    }

    private static String currentIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr()
                : null;
    }

    private static Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}