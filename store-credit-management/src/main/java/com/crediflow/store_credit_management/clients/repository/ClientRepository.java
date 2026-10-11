package com.crediflow.store_credit_management.clients.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.domain.model.ClientStatus;

/**
 * Client Repository
 * @summary
 * Acceso a datos de clientes. Todas las consultas de uso normal reciben el
 * storeId, de modo que una tienda nunca pueda leer clientes de otra.
 * NO se usa findById(...) en los servicios: ese método ignora la tienda.
 *
 * @since 1.0.0
 */
public interface ClientRepository extends JpaRepository<Client, Long> {
    /** Busca un cliente SOLO si pertenece a la tienda indicada (evita IDOR). */
    Optional<Client> findByIdAndStoreId(Long id, Long storeId);

    /** Lista paginada de los clientes de una tienda. */
    Page<Client> findByStoreId(Long storeId, Pageable pageable);

    /** Lista paginada filtrada por estado (por ejemplo, solo los ACTIVE). */
    Page<Client> findByStoreIdAndStatus(Long storeId, ClientStatus status, Pageable pageable);

    /** Valida que el documento no esté repetido dentro de la misma tienda. */
    boolean existsByStoreIdAndDocumentNumber(Long storeId, String documentNumber);

    /**
     * Clientes asociados a un usuario de acceso. Es una lista porque la misma
     * persona puede ser cliente de varias tiendas (decisión #12), y cada
     * tienda tiene su propio registro.
     */
    List<Client> findByUserId(Long userId);
}
