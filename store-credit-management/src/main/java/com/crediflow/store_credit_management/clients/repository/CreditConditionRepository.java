package com.crediflow.store_credit_management.clients.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;

/**
 * Credit Condition Repository
 * @summary
 * Acceso a datos de condiciones de crédito. Una condición no tiene storeId
 * propio: su tienda es la de su cliente. Por eso el servicio debe cargar
 * PRIMERO el cliente con ClientRepository.findByIdAndStoreId(...) y recién
 * después pedir sus condiciones por el id de ese cliente ya validado.
 *
 * @since 1.0.0
 */
public interface CreditConditionRepository extends JpaRepository<CreditCondition, Long>{

    /** La condición vigente de un cliente (a lo sumo una). */
    Optional<CreditCondition> findByClient_IdAndActiveTrue(Long clientId);

    /** Historial completo de condiciones, de la más reciente a la más antigua. */
    List<CreditCondition> findByClient_IdOrderByValidFromDescIdDesc(Long clientId);
}
