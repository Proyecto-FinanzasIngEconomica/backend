package com.crediflow.store_credit_management.iam.service;

import com.crediflow.store_credit_management.stores.service.StoreAdminProvisioner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Iam Store Admin Provisioner
 * @summary
 * Cumple el puerto de `stores` delegando en AccountService.
 *
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
class IamStoreAdminProvisioner implements StoreAdminProvisioner {

    private final AccountService accountService;

    @Override
    public void createStoreAdmin(Long storeId, String username, String password) {
        accountService.createStoreAdmin(storeId, username, password);
    }
}
