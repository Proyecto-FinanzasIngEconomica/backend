package com.crediflow.store_credit_management.iam.dto;

import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Auth DTOs
 * @summary
 * Objetos de entrada (Request) y salida (Resource) del módulo iam para el
 * inicio de sesión.
 *
 * @since 1.0.0
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Schema(example = "admin")
            @NotBlank @Size(max = 50) String username,

            @Schema(example = "Admin12345")
            @NotBlank @Size(max = 100) String password
    ) {
        /** Nunca imprime la contraseña si el objeto llega a un log. */
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    public record UserResource(
            Long id,
            String username,
            UserRole role,
            @Schema(description = "Tienda del usuario; null para SYSTEM_ADMIN")
            Long storeId,
            @Schema(description = "Cliente vinculado; solo para el rol CLIENT")
            Long clientId
    ) {
        public static UserResource from(UserAccount account, Long clientId) {
            return new UserResource(
                    account.getId(),
                    account.getUsername(),
                    account.getRole(),
                    account.getStoreId(),
                    clientId);
        }
    }

    public record LoginResponse(
            @Schema(description = "JWT para el encabezado Authorization: Bearer <token>")
            String accessToken,
            String tokenType,
            @Schema(description = "Instante de vencimiento del token, en UTC")
            Instant expiresAt,
            UserResource user
    ) {
        public static LoginResponse from(String accessToken, Instant expiresAt, UserAccount account, Long clientId) {
            return new LoginResponse(accessToken, "Bearer", expiresAt, UserResource.from(account, clientId));
        }
    }
}
