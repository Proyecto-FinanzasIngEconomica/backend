package com.crediflow.store_credit_management.shared.interfaces.rest.exception;

/**
 * Access Forbidden Exception
 * @summary
 * El usuario está autenticado pero no tiene permiso para esta operación
 * concreta (por ejemplo, un administrador de tienda intentando gestionar a
 * otro administrador). Se traduce a 403.
 *
 * @since 1.0.0
 */
public class AccessForbiddenException extends RuntimeException {

    public AccessForbiddenException(String message) {
        super(message);
    }
}
