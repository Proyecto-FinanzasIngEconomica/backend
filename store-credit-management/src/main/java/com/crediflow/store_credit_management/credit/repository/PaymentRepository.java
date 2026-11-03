package com.crediflow.store_credit_management.credit.repository;

import com.crediflow.store_credit_management.credit.domain.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Payment Repository
 * @summary
 * Acceso a datos de pagos. Igual que en los demás módulos, toda consulta
 * recibe el storeId para que una tienda nunca lea pagos de otra. NO se usa
 * findById(...) en los servicios.
 *
 * @since 1.0.0
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Busca un pago SOLO si pertenece a la tienda indicada (evita IDOR). */
    Optional<Payment> findByIdAndStoreId(Long id, Long storeId);

    /** Lista paginada de los pagos de la tienda. */
    Page<Payment> findByStoreId(Long storeId, Pageable pageable);

    /** Lista paginada de los pagos de un cliente de la tienda. */
    Page<Payment> findByStoreIdAndClientId(Long storeId, Long clientId, Pageable pageable);
}
