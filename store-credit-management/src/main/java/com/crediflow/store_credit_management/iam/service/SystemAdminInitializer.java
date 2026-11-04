package com.crediflow.store_credit_management.iam.service;

import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import com.crediflow.store_credit_management.iam.repository.UserAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * System Admin Initializer
 * @summary
 * Crea el primer administrador del sistema al arrancar, con las credenciales
 * de las variables de entorno CREDIFLOW_ADMIN_USERNAME y
 * CREDIFLOW_ADMIN_PASSWORD (en el perfil dev vienen de application-dev.properties).
 *
 * Solo actúa si todavía no existe ningún SYSTEM_ADMIN: es idempotente y nunca
 * toca una cuenta existente. Si faltan las credenciales, avisa y sigue.
 *
 * @since 1.0.0
 */
@Slf4j
@Component
class SystemAdminInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    SystemAdminInitializer(UserAccountRepository repository,
                           PasswordEncoder passwordEncoder,
                           @Value("${crediflow.bootstrap.admin-username:}") String username,
                           @Value("${crediflow.bootstrap.admin-password:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.existsByRole(UserRole.SYSTEM_ADMIN)) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            log.warn("No existe ningún administrador del sistema y no se definieron "
                    + "CREDIFLOW_ADMIN_USERNAME y CREDIFLOW_ADMIN_PASSWORD: no se creará ninguno");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "La contraseña del administrador inicial debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        repository.save(UserAccount.register(username, passwordEncoder.encode(password), UserRole.SYSTEM_ADMIN, null));
        log.info("Administrador del sistema inicial creado: {}", username);
    }
}
