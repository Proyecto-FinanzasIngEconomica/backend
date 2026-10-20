    package com.crediflow.store_credit_management.catalog.repository;

import com.crediflow.store_credit_management.catalog.domain.model.Product;
import com.crediflow.store_credit_management.catalog.domain.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Product Repository
 * @summary
 * Acceso a datos de productos. Igual que en clients, toda consulta recibe el
 * storeId: una tienda nunca puede leer productos de otra. NO se usa
 * findById(...) en los servicios porque ignora la tienda.
 *
 * @since 1.0.0
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Busca un producto SOLO si pertenece a la tienda indicada (evita IDOR). */
    Optional<Product> findByIdAndStoreId(Long id, Long storeId);

    /** Lista paginada de los productos de una tienda. */
    Page<Product> findByStoreId(Long storeId, Pageable pageable);

    /** Lista paginada filtrada por estado (por ejemplo, solo los ACTIVE). */
    Page<Product> findByStoreIdAndStatus(Long storeId, ProductStatus status, Pageable pageable);
}