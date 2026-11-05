package com.crediflow.store_credit_management.audit.domain.model;

/**
 * Audit Action
 * @summary
 * Acciones que quedan registradas. Agregar una nueva es agregar un valor aquí
 * y una llamada a AuditService donde ocurre.
 *
 * @since 1.0.0
 */
public enum AuditAction {
    LOGIN,
    LOGIN_FAILED,
    PURCHASE_REGISTERED,
    PAYMENT_REGISTERED
}