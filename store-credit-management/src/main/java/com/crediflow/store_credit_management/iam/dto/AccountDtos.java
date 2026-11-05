package com.crediflow.store_credit_management.iam.dto;

import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import com.crediflow.store_credit_management.iam.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Account DTOs
 * @summary
 * Entrada y salida de la gestión de cuentas. Ninguna salida incluye jamás la
 * contraseña ni su hash.
 *
 * @since 1.0.0
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @Schema(example = "juanperez")
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4,50}",
                    message = "debe tener entre 4 y 50 letras o números")
            String username,

            @NotBlank @Size(min = 8, max = 100) String password
    ) {
        @Override
        public String toString() {
            return "CreateAccountRequest[username=" + username + ", password=***]";
        }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
        @Override
        public String toString() {
            return "ResetPasswordRequest[newPassword=***]";
        }
    }

    public record AccountResource(
            Long id,
            String username,
            UserRole role,
            UserStatus status,
            Long storeId,
            int failedAttempts,
            LocalDateTime lastAccess
    ) {
        public static AccountResource from(UserAccount account) {
            return new AccountResource(
                    account.getId(),
                    account.getUsername(),
                    account.getRole(),
                    account.getStatus(),
                    account.getStoreId(),
                    account.getFailedAttempts(),
                    account.getLastAccess());
        }
    }
}
