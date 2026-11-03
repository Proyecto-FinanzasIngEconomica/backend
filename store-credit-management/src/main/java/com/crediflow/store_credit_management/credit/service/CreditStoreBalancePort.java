package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.credit.domain.model.InstallmentStatus;
import com.crediflow.store_credit_management.credit.repository.PurchaseInstallmentRepository;
import com.crediflow.store_credit_management.stores.service.StoreBalancePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Credit Store Balance Port
 * @summary
 * Una tienda tiene cobros pendientes si alguna cuota de sus compras sigue sin pagar.
 *
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
class CreditStoreBalancePort implements StoreBalancePort {

    private final PurchaseInstallmentRepository installmentRepository;

    @Override
    public boolean hasOutstandingBalance(Long storeId) {
        return installmentRepository.existsByPurchase_StoreIdAndStatus(storeId, InstallmentStatus.PENDING);
    }
}
