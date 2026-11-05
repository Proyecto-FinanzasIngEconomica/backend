package com.crediflow.store_credit_management.iam.service;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import com.crediflow.store_credit_management.iam.domain.model.UserStatus;
import com.crediflow.store_credit_management.iam.repository.UserAccountRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account Service
 * @summary
 * Alta, consulta, desbloqueo, baja y restablecimiento de contraseña de las
 * cuentas. Todo se hace dentro de una tienda: cada consulta lleva el storeId,
 * que debe venir del token (lo exigirá la parte 5), nunca de lo que escriba el usuario.
 *
 * No depende de StoreService a propósito: StoreService usa el puerto
 * StoreAdminProvisioner, y eso crearía un ciclo entre beans.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ClientService clientService;

    /** Cuenta del administrador de una tienda (se llama al registrar la tienda). */
    @Transactional
    public UserAccount createStoreAdmin(Long storeId, String username, String password) {
        return create(username, password, UserRole.STORE_ADMIN, storeId);
    }

    /**
     * Cuenta de acceso de un cliente. Bloquea la fila del cliente para que dos
     * altas simultáneas no le creen dos cuentas.
     */
    @Transactional
    public UserAccount createClientAccount(Long storeId, Long clientId, String username, String password) {
        Client client = clientService.getForUpdate(storeId, clientId);
        if (!client.isActive()) {
            throw new BussinessRuleException("El cliente está dado de baja");
        }
        if (client.getUserId() != null) {
            throw new BussinessRuleException("El cliente ya tiene una cuenta de acceso");
        }
        UserAccount account = create(username, password, UserRole.CLIENT, storeId);
        client.linkUser(account.getId());
        return account;
    }

    @Transactional(readOnly = true)
    public UserAccount get(Long storeId, Long accountId) {
        return repository.findByIdAndStoreId(accountId, storeId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Cuenta de usuario", accountId.toString()));
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> list(Long storeId, Pageable pageable) {
        return repository.findByStoreId(storeId, pageable);
    }

    @Transactional
    public UserAccount unblock(Long storeId, Long accountId) {
        UserAccount account = get(storeId, accountId);
        if (account.getStatus() != UserStatus.BLOCKED) {
            throw new BussinessRuleException("La cuenta no está bloqueada");
        }
        account.unblock();
        return account;
    }

    /** Baja de la cuenta. La tienda nunca se queda sin un administrador activo. */
    @Transactional
    public UserAccount deactivate(Long storeId, Long accountId) {
        UserAccount account = get(storeId, accountId);
        if (account.getStatus() == UserStatus.INACTIVE) {
            throw new BussinessRuleException("La cuenta ya está dada de baja");
        }
        if (account.getRole() == UserRole.STORE_ADMIN && account.isActive()
                && repository.countByStoreIdAndRoleAndStatus(storeId, UserRole.STORE_ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BussinessRuleException(
                    "No se puede dar de baja al único administrador activo de la tienda");
        }
        account.deactivate();
        return account;
    }

    /** Restablece la contraseña. No desbloquea la cuenta: eso es una acción aparte. */
    @Transactional
    public UserAccount resetPassword(Long storeId, Long accountId, String newPassword) {
        UserAccount account = get(storeId, accountId);
        if (account.getStatus() == UserStatus.INACTIVE) {
            throw new BussinessRuleException("La cuenta está dada de baja");
        }
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        return account;
    }

    private UserAccount create(String username, String password, UserRole role, Long storeId) {
        String normalized = UserAccount.normalizeUsername(username);
        if (repository.existsByUsername(normalized)) {
            throw new BussinessRuleException("El usuario " + normalized + " ya existe");
        }
        return repository.save(UserAccount.register(normalized, passwordEncoder.encode(password), role, storeId));
    }
}
