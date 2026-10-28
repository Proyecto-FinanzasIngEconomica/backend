package com.crediflow.store_credit_management.credit.repository;

import com.crediflow.store_credit_management.credit.domain.model.Statement;
import com.crediflow.store_credit_management.credit.domain.model.StatementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Statement Repository
 * @summary
 * Acceso a datos de estados de cuenta. Igual que en los demás módulos, las
 * consultas de uso normal reciben el storeId para que una tienda nunca lea
 * estados de otra. NO se usa findById(...) en los servicios.
 *
 * @since 1.0.0
 */
public interface StatementRepository extends JpaRepository<Statement, Long> {

    /** Busca un estado SOLO si pertenece a la tienda indicada (evita IDOR). */
    Optional<Statement> findByIdAndStoreId(Long id, Long storeId);

    /** Lista paginada de los estados de cuenta de la tienda. */
    Page<Statement> findByStoreId(Long storeId, Pageable pageable);

    /** Lista paginada de los estados de un cliente de la tienda. */
    Page<Statement> findByStoreIdAndClientId(Long storeId, Long clientId, Pageable pageable);

    /** ¿Ya se emitió el estado de este ciclo para el cliente? Hace idempotente la generación. */
    boolean existsByClientIdAndCycleCutoffDate(Long clientId, LocalDate cycleCutoffDate);

    /**
     * ¿Existe un estado del cliente cuya fecha de pago sea esa o posterior? Un
     * estado incluye TODAS las cuotas pendientes que vencen hasta su fecha de
     * pago; si una cuota nueva vence en ese rango, ya quedó fuera de él.
     */
    boolean existsByClientIdAndPaymentDateGreaterThanEqual(Long clientId, LocalDate date);

    /** Estados del cliente en un estado dado (los ISSUED, para pasarlos a SUPERSEDED). */
    List<Statement> findByClientIdAndStatus(Long clientId, StatementStatus status);
}
