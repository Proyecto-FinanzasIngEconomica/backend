package com.crediflow.store_credit_management.stores.repository;

import com.crediflow.store_credit_management.stores.domain.model.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Business Type Repository
 * @summary
 * Acceso al catálogo de giros de negocio. Es un catálogo global, no pertenece
 * a ninguna tienda, por eso aquí no hay filtro por storeId.
 *
 * @since 1.0.0
 */
public interface BusinessTypeRepository extends JpaRepository<BusinessType, Long> {

    /** Catálogo completo ordenado por nombre, para llenar listas desplegables. */
    List<BusinessType> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
