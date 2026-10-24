package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.clients.service.ClientBalancePort;
import com.crediflow.store_credit_management.credit.domain.model.InstallmentStatus;
import com.crediflow.store_credit_management.credit.repository.PurchaseInstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Credit Client Balance Port
 * @summary
 * Implementación real del puerto que declara clients: un cliente tiene saldo
 * pendiente si le queda alguna cuota sin pagar. Reemplaza al
 * PlaceholderClientBalancePort.
 *
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
class CreditClientBalancePort implements ClientBalancePort {

    private final PurchaseInstallmentRepository installmentRepository;

    @Override
    public boolean hasOutstandingBalance(Long clientId) {
        return installmentRepository.existsByPurchase_ClientIdAndStatus(clientId, InstallmentStatus.PENDING);
    }
}
