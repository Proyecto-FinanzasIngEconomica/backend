package com.crediflow.store_credit_management.stores.repository;

import com.crediflow.store_credit_management.stores.domain.model.Store;
import com.crediflow.store_credit_management.stores.domain.model.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Store Repository
 * @summary
 * Acceso a datos de tiendas. A diferencia de los demás módulos, las tiendas
 * NO se filtran por storeId: una tienda ES el inquilino. Solo el
 * administrador del sistema las gestiona, y eso se exigirá en la capa de
 * seguridad (IAM), no aquí.
 *
 * @since 1.0.0
 */
public interface StoreRepository extends JpaRepository<Store, Long> {

    /** Lista paginada filtrada por estado (por ejemplo, solo las ACTIVE). */
    Page<Store> findByStatus(StoreStatus status, Pageable pageable);

    /** Valida que el RUC no esté repetido. */
    boolean existsByRuc(String ruc);
}