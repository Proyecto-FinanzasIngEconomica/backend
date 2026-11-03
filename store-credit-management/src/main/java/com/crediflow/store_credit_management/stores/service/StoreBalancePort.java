package com.crediflow.store_credit_management.stores.service;

/**
 * Store Balance Port
 * @summary
 * Pregunta que stores le hace al módulo de crédito sin conocerlo: ¿la tienda
 * tiene cobros pendientes? La implementa credit (CreditStoreBalancePort).
 * Es el mismo patrón que ClientBalancePort: el módulo que necesita el dato
 * declara el puerto y evita depender del que lo calcula.
 *
 * @since 1.0.0
 */
public interface StoreBalancePort {

    boolean hasOutstandingBalance(Long storeId);
}
