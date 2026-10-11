package com.crediflow.store_credit_management.clients.service;

/**
 * Client Balance Port
 * @summary
 * Puerto que el módulo clients necesita del módulo credit: saber si un
 * cliente tiene deuda. Así clients no depende de las compras ni de los
 * listados; credit implementará esta interfaz cuando exista.
 *
 * @since 1.0.0
 */
public interface ClientBalancePort {
    
    boolean hasOutstandingBalance(Long clientId);
}
