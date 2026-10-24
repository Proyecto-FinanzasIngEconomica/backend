package com.crediflow.store_credit_management.credit.repository;

import com.crediflow.store_credit_management.credit.domain.model.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Purchase Repository
 * @summary
 * Acceso a datos de compras. Igual que en clients y catalog, toda consulta
 * recibe el storeId: una tienda nunca puede leer compras de otra. NO se usa
 * findById(...) en los servicios porque ignora la tienda.
 *
 * @since 1.0.0
 */
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    /** Busca una compra SOLO si pertenece a la tienda indicada (evita IDOR). */
    Optional<Purchase> findByIdAndStoreId(Long id, Long storeId);

    /** Lista paginada de todas las compras de la tienda. */
    Page<Purchase> findByStoreId(Long storeId, Pageable pageable);

    /** Lista paginada de las compras de un cliente de la tienda. */
    Page<Purchase> findByStoreIdAndClientId(Long storeId, Long clientId, Pageable pageable);
}
