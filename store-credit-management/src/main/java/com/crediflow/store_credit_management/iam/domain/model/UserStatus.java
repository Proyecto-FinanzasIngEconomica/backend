package com.crediflow.store_credit_management.iam.domain.model;

/**
 * User Status
 * @summary
 * Estado de una cuenta. BLOCKED se produce solo, tras demasiados intentos de
 * acceso fallidos seguidos, y exige que un administrador la desbloquee.
 * INACTIVE es una baja decidida por un administrador.
 *
 * @since 1.0.0
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}