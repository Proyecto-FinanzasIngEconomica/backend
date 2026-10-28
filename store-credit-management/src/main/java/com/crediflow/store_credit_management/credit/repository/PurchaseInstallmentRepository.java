package com.crediflow.store_credit_management.credit.repository;

import com.crediflow.store_credit_management.credit.domain.model.InstallmentStatus;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Purchase Installment Repository
 * @summary
 * Consultas sobre cuotas. La más importante es el capital pendiente de un
 * cliente: es lo que consume su línea de crédito. El cupo NO se guarda en
 * ninguna columna, siempre se calcula desde aquí, para que no pueda quedar
 * desactualizado.
 *
 * @since 1.0.0
 */
public interface PurchaseInstallmentRepository extends JpaRepository<PurchaseInstallment, Long> {

    /**
     * Suma del capital de las cuotas en el estado indicado. Devuelve null si
     * no hay ninguna (la suma de nada es null en SQL); por eso se usa a
     * través de {@link #outstandingPrincipal}.
     */
    @Query("""
            select sum(i.principalPart)
            from PurchaseInstallment i
            where i.purchase.storeId = :storeId
              and i.purchase.clientId = :clientId
              and i.status = :status
            """)
    BigDecimal sumPrincipalByStatus(@Param("storeId") Long storeId,
                                    @Param("clientId") Long clientId,
                                    @Param("status") InstallmentStatus status);

    /** Capital pendiente de pago de un cliente en una tienda; cero si no debe nada. */
    default BigDecimal outstandingPrincipal(Long storeId, Long clientId) {
        BigDecimal total = sumPrincipalByStatus(storeId, clientId, InstallmentStatus.PENDING);
        return total == null ? BigDecimal.ZERO : total;
    }

    /** ¿Tiene el cliente alguna cuota sin pagar? Sirve para bloquear su baja. */
    boolean existsByPurchase_ClientIdAndStatus(Long clientId, InstallmentStatus status);

    /**
     * Cuotas del cliente en el estado indicado que vencen en la fecha dada o
     * antes, ordenadas por fecha de compra. Trae la compra en la misma consulta.
    */
    @Query("""
            select i
            from PurchaseInstallment i
            join fetch i.purchase p
            where p.storeId = :storeId
              and p.clientId = :clientId
              and i.status = :status
              and i.dueDate <= :dueDate
            order by p.purchasedAt asc, i.installmentNumber asc
            """)
    List<PurchaseInstallment> listByStatusDueUpTo(@Param("storeId") Long storeId,
                                                  @Param("clientId") Long clientId,
                                                  @Param("status") InstallmentStatus status,
                                                  @Param("dueDate") LocalDate dueDate);

    /** Cuotas pendientes (por pagar) que vencen hasta la fecha indicada: lo que lista un estado de cuenta. */
    default List<PurchaseInstallment> pendingDueUpTo(Long storeId, Long clientId, LocalDate dueDate) {
        return listByStatusDueUpTo(storeId, clientId, InstallmentStatus.PENDING, dueDate);
    }
}