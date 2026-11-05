package com.crediflow.store_credit_management.shared.interfaces.rest.exception;

/**
 * Authentication Failed Exception
 * @summary
 * El inicio de sesión no se pudo completar: credenciales incorrectas, cuenta
 * bloqueada o dada de baja. Se traduce a 401.
 *
 * @since 1.0.0
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
