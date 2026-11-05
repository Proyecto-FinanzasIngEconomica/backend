package com.crediflow.store_credit_management.iam.service;

import com.crediflow.store_credit_management.audit.domain.model.AuditAction;
import com.crediflow.store_credit_management.audit.service.AuditService;
import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import com.crediflow.store_credit_management.iam.domain.model.UserStatus;
import com.crediflow.store_credit_management.iam.repository.UserAccountRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.AuthenticationFailedException;
import com.crediflow.store_credit_management.stores.domain.model.Store;
import com.crediflow.store_credit_management.stores.service.StoreService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Auth Service
 * @summary
 * Inicio de sesión: verifica usuario y contraseña, controla los intentos
 * fallidos (la cuenta se bloquea al quinto seguido), comprueba que la tienda
 * y el cliente de la cuenta sigan activos y emite el token.
 *
 * @since 1.0.0
 */
@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Usuario o contraseña incorrectos";

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final StoreService storeService;
    private final ClientService clientService;
    private final AuditService auditService;

    /**
     * Hash que se compara cuando el usuario no existe, para que responder
     * tarde lo mismo y no se pueda descubrir qué usuarios existen midiendo el tiempo.
     */
    private final String dummyHash;

    public AuthService(UserAccountRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       StoreService storeService,
                       ClientService clientService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.storeService = storeService;
        this.clientService = clientService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("contraseña-de-relleno");
    }

    /** Resultado de un inicio de sesión correcto. */
    public record LoginResult(TokenService.IssuedToken token, UserAccount account, Long clientId) {
    }

    /**
     * Inicia sesión. El contador de intentos fallidos DEBE guardarse aunque el
     * login falle, por eso la transacción no se revierte con esta excepción
     * (noRollbackFor); si no, el bloqueo nunca se aplicaría.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginResult login(String username, String password) {
        String normalized = UserAccount.normalizeUsername(username);
        UserAccount account = userRepository.findByUsername(normalized).orElse(null);
        if (account == null) {
            passwordEncoder.matches(password, dummyHash);
            throw fail(null, normalized, "usuario inexistente", BAD_CREDENTIALS);
        }
        if (account.getStatus() == UserStatus.BLOCKED) {
            throw fail(account, normalized, "cuenta bloqueada",
                    "La cuenta está bloqueada por intentos fallidos; pide a un administrador que la desbloquee");
        }
        if (account.getStatus() == UserStatus.INACTIVE) {
            throw fail(account, normalized, "cuenta dada de baja", "La cuenta está dada de baja");
        }

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            boolean nowBlocked = account.recordFailedAttempt();
            throw fail(account, normalized,
                    nowBlocked ? "contraseña incorrecta; cuenta bloqueada" : "contraseña incorrecta",
                    nowBlocked
                            ? "La cuenta fue bloqueada tras " + UserAccount.MAX_FAILED_ATTEMPTS
                                    + " intentos fallidos seguidos; pide a un administrador que la desbloquee"
                            : BAD_CREDENTIALS);
        }

        Long clientId = resolveContext(account);
        account.recordSuccessfulLogin(LocalDateTime.now());
        auditService.recordAs(AuditAction.LOGIN, account.getId(), account.getUsername(),
                account.getStoreId(), null);
        return new LoginResult(tokenService.issue(account, clientId), account, clientId);
    }

    /**
     * Con la contraseña ya correcta, comprueba que la tienda siga activa y, para
     * un CLIENT, que su cliente exista, esté activo y sea de esa tienda.
     * Devuelve el id del cliente (solo para el rol CLIENT).
     */
    private Long resolveContext(UserAccount account) {
        if (account.getRole() == UserRole.SYSTEM_ADMIN) {
            return null;
        }
        Store store = storeService.get(account.getStoreId());
        if (!store.isActive()) {
            throw fail(account, account.getUsername(), "tienda dada de baja", "La tienda está dada de baja");
        }
        if (account.getRole() == UserRole.STORE_ADMIN) {
            return null;
        }
        List<Client> clients = clientService.findByUserId(account.getId());
        if (clients.size() != 1) {
            throw fail(account, account.getUsername(), "sin cliente vinculado",
                    "La cuenta no está vinculada a ningún cliente");
        }
        Client client = clients.get(0);
        if (!client.isActive() || !client.getStoreId().equals(account.getStoreId())) {
            throw fail(account, account.getUsername(), "cliente dado de baja",
                    "El cliente de esta cuenta está dado de baja");
        }
        return client.getId();
    }

    /** Registra el intento fallido en la auditoría y devuelve la excepción para lanzarla. */
    private AuthenticationFailedException fail(UserAccount account, String attemptedUsername,
                                               String reason, String message) {
        auditService.recordAs(AuditAction.LOGIN_FAILED,
                account == null ? null : account.getId(),
                attemptedUsername,
                account == null ? null : account.getStoreId(),
                reason);
        return new AuthenticationFailedException(message);
    }
}
