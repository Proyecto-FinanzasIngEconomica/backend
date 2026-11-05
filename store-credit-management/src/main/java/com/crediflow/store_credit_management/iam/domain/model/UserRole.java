package com.crediflow.store_credit_management.iam.domain.model;

/**
 * User Role
 * @summary
 * Perfiles de acceso del sistema.
 * SYSTEM_ADMIN: da de alta y baja tiendas; no pertenece a ninguna.
 * STORE_ADMIN: administra productos, clientes, compras y pagos de SU tienda.
 * CLIENT: consulta su propio estado de cuenta, cronograma y listados.
 *
 * @since 1.0.0
 */
public enum UserRole {
    SYSTEM_ADMIN,
    STORE_ADMIN,
    CLIENT
}