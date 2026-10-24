package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;
import com.crediflow.store_credit_management.clients.service.CreditConditionService;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.CreditLineResource;
import com.crediflow.store_credit_management.credit.repository.PurchaseInstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Credit Line Service
 * @summary
 * Calcula el estado de la línea de crédito de un cliente (modelo revolvente,
 * decisión #11): usado = capital pendiente de sus cuotas; disponible = límite
 * menos usado. Nada de esto se guarda: se calcula en cada consulta, así que no
 * puede quedar desactualizado.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class CreditLineService {

    private final CreditConditionService creditConditionService;
    private final PurchaseInstallmentRepository installmentRepository;

    /** Estado de la línea con la condición vigente; 404 si el cliente no tiene una. */
    @Transactional(readOnly = true)
    public CreditLineResource get(Long storeId, Long clientId) {
        CreditCondition condition = creditConditionService.getActive(storeId, clientId);
        BigDecimal limit = condition.getCreditLimit().amount();
        BigDecimal used = installmentRepository.outstandingPrincipal(storeId, clientId);
        // Si el límite se bajó por debajo de la deuda, se muestra 0, no un negativo.
        BigDecimal available = limit.subtract(used).max(BigDecimal.ZERO);
        return new CreditLineResource(condition.currency(), limit, used, available);
    }

    /**
     * Cupo disponible SIN recortar a cero. Lo usa el registro de compras: un
     * valor negativo significa que el cliente ya excede su límite, y cualquier
     * compra nueva debe rechazarse.
     */
    @Transactional(readOnly = true)
    public BigDecimal availableAmount(Long storeId, Long clientId, CreditCondition condition) {
        return condition.getCreditLimit().amount()
                .subtract(installmentRepository.outstandingPrincipal(storeId, clientId));
    }
}
