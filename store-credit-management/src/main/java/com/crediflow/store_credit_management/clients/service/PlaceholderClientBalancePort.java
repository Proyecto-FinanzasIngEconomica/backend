package com.crediflow.store_credit_management.clients.service;

import org.springframework.stereotype.Component;

/**
 * Placeholder Client Balance Port
 * @summary
 * IMPLEMENTACIÓN TEMPORAL: dice que ningún cliente tiene deuda, porque todavía
 * no existe el módulo de compras. BORRAR ESTA CLASE cuando credit implemente
 * {@link ClientBalancePort}.
 *
 * Si se olvida borrarla, la aplicación NO arranca: Spring encontrará dos
 * implementaciones del puerto y lo avisará con un error claro. Ese fallo
 * ruidoso es a propósito, para que nunca quede activa por descuido una
 * validación que siempre responde "sin deuda".
 *
 * @since 1.0.0
 */
@Component
class PlaceholderClientBalancePort implements ClientBalancePort {

    @Override
    public boolean hasOutstandingBalance(Long clientId) {
        return false;
    }
}
