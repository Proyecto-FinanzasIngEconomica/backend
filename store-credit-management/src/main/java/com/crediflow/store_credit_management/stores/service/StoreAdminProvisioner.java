    package com.crediflow.store_credit_management.stores.service;

/**
 * Store Admin Provisioner
 * @summary
 * Puerto: crear la cuenta del administrador de una tienda recién registrada.
 * `stores` declara lo que necesita e `iam` lo cumple, así `stores` nunca
 * importa nada de `iam` (mismo patrón que ClientBalancePort).
 *
 * Se llama dentro de la transacción del alta de la tienda: si la cuenta no se
 * puede crear (usuario repetido), la tienda tampoco se guarda.
 *
 * @since 1.0.0
 */
public interface StoreAdminProvisioner {

    void createStoreAdmin(Long storeId, String username, String password);
}
