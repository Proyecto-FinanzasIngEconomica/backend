package com.crediflow.store_credit_management.iam.service;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import com.crediflow.store_credit_management.iam.domain.model.UserStatus;
import com.crediflow.store_credit_management.iam.repository.UserAccountRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.AccessForbiddenException;
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
 * cuentas, más el cambio de contraseña propio.
 *
 * Quién gestiona a quién: el STORE_ADMIN gestiona únicamente cuentas CLIENT de
 * su tienda; el SYSTEM_ADMIN gestiona únicamente cuentas STORE_ADMIN. El rol
 * del que actúa (actorRole) sale del token, nunca de la petición.
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

    /** Solo lista las cuentas que el actor puede gestionar. */
    @Transactional(readOnly = true)
    public Page<UserAccount> list(Long storeId, UserRole actorRole, Pageable pageable) {
        return repository.findByStoreIdAndRole(storeId, manageableRole(actorRole), pageable);
    }

    @Transactional
    public UserAccount unblock(Long storeId, Long accountId, UserRole actorRole) {
        UserAccount account = getManageable(storeId, accountId, actorRole);
        if (account.getStatus() != UserStatus.BLOCKED) {
            throw new BussinessRuleException("La cuenta no está bloqueada");
        }
        account.unblock();
        return account;
    }

    /** Baja de la cuenta. La tienda nunca se queda sin un administrador activo. */
    @Transactional
    public UserAccount deactivate(Long storeId, Long accountId, UserRole actorRole) {
        UserAccount account = getManageable(storeId, accountId, actorRole);
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
    public UserAccount resetPassword(Long storeId, Long accountId, String newPassword, UserRole actorRole) {
        UserAccount account = getManageable(storeId, accountId, actorRole);
        if (account.getStatus() == UserStatus.INACTIVE) {
            throw new BussinessRuleException("La cuenta está dada de baja");
        }
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        return account;
    }

    /**
     * Cambio de contraseña por su propio dueño. Una contraseña actual
     * equivocada cuenta como intento fallido (si alguien robara un token no
     * podría adivinar la contraseña sin bloquear la cuenta), por eso la
     * transacción no se revierte con esta excepción.
     */
    @Transactional(noRollbackFor = BussinessRuleException.class)
    public void changeOwnPassword(Long accountId, String currentPassword, String newPassword) {
        UserAccount account = repository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("La cuenta no existe"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            account.recordFailedAttempt();
            throw new BussinessRuleException("La contraseña actual es incorrecta");
        }
        if (currentPassword.equals(newPassword)) {
            throw new BussinessRuleException("La nueva contraseña debe ser distinta de la actual");
        }
        account.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    private UserAccount getManageable(Long storeId, Long accountId, UserRole actorRole) {
        UserAccount account = repository.findByIdAndStoreId(accountId, storeId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Cuenta", accountId.toString()));
        if (account.getRole() != manageableRole(actorRole)) {
            throw new AccessForbiddenException("No tienes permiso para gestionar esta cuenta");
        }
        return account;
    }

    private static UserRole manageableRole(UserRole actorRole) {
        return switch (actorRole) {
            case SYSTEM_ADMIN -> UserRole.STORE_ADMIN;
            case STORE_ADMIN -> UserRole.CLIENT;
            case CLIENT -> throw new AccessForbiddenException("No tienes permiso para gestionar cuentas");
        };
    }

    private UserAccount create(String username, String password, UserRole role, Long storeId) {
        String normalized = UserAccount.normalizeUsername(username);
        if (repository.existsByUsername(normalized)) {
            throw new BussinessRuleException("El usuario " + normalized + " ya existe");
        }
        return repository.save(UserAccount.register(normalized, passwordEncoder.encode(password), role, storeId));
    }
}