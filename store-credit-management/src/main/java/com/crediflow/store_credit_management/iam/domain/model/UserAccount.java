package com.crediflow.store_credit_management.iam.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * User Account
 * @summary
 * Credenciales de acceso de los tres perfiles. La contraseña llega YA
 * cifrada (con BCrypt, desde el servicio): la entidad nunca ve ni guarda el
 * texto plano.
 *
 * Control de intentos: tras {@value #MAX_FAILED_ATTEMPTS} fallos consecutivos
 * la cuenta pasa a BLOCKED; un acceso correcto reinicia el contador.
 *
 * El nombre de usuario se guarda en minúsculas para que "Pepe" y "pepe" no
 * puedan ser dos cuentas distintas.
 *
 * Se referencia a la tienda por id; el vínculo con el cliente (para el rol
 * CLIENT) vive en Client.userId.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends AuditableModel {

    public static final int MAX_FAILED_ATTEMPTS = 5;

    private static final Pattern USERNAME_FORMAT = Pattern.compile("[a-z0-9]{4,50}");

    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 15)
    private UserRole role;

    /** Tienda a la que pertenece. Nula solo para SYSTEM_ADMIN. */
    @Column(updatable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    @Column(nullable = false)
    private int failedAttempts;

    @Column
    private LocalDateTime lastAccess;

    /**
     * Crea una cuenta. Es la única forma de hacerlo, y verifica que el rol y
     * la tienda sean coherentes: el administrador del sistema no tiene tienda,
     * y todos los demás sí.
     */
    public static UserAccount register(String username, String passwordHash, UserRole role, Long storeId) {
        if (role == null) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El usuario debe tener entre 4 y 50 caracteres alfanuméricos");
        }
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 100) {
            throw new IllegalArgumentException("La contraseña cifrada no es válida");
        }
        if (role == UserRole.SYSTEM_ADMIN && storeId != null) {
            throw new IllegalArgumentException("El administrador del sistema no pertenece a ninguna tienda");
        }
        if (role != UserRole.SYSTEM_ADMIN && storeId == null) {
            throw new IllegalArgumentException("Los administradores de tienda y los clientes requieren una tienda");
        }
        UserAccount account = new UserAccount();
        account.username = normalized;
        account.passwordHash = passwordHash;
        account.role = role;
        account.storeId = storeId;
        account.status = UserStatus.ACTIVE;
        account.failedAttempts = 0;
        return account;
    }

    /** Normaliza un nombre de usuario como se guarda, para buscarlo en el login. */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Acceso correcto: reinicia el contador de fallos y anota el último ingreso. */
    public void recordSuccessfulLogin(LocalDateTime when) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Solo una cuenta activa puede iniciar sesión");
        }
        this.failedAttempts = 0;
        this.lastAccess = when;
    }

    /**
     * Acceso fallido: suma un intento y, al llegar al máximo, bloquea la
     * cuenta. Devuelve true si ESTE intento la bloqueó. Una cuenta que ya no
     * está activa no acumula intentos.
     */
    public boolean recordFailedAttempt() {
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        this.failedAttempts++;
        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.status = UserStatus.BLOCKED;
            return true;
        }
        return false;
    }

    /** Un administrador desbloquea una cuenta bloqueada por intentos fallidos. */
    public void unblock() {
        if (status != UserStatus.BLOCKED) {
            throw new IllegalStateException("La cuenta no está bloqueada");
        }
        this.status = UserStatus.ACTIVE;
        this.failedAttempts = 0;
    }

    /** Baja de la cuenta: deja de poder iniciar sesión, pero se conserva. */
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    /** Cambia la contraseña (ya cifrada). */
    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank() || newPasswordHash.length() > 100) {
            throw new IllegalArgumentException("La contraseña cifrada no es válida");
        }
        this.passwordHash = newPasswordHash;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
