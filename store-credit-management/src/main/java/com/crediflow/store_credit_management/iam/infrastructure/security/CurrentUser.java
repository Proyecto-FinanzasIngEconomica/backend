package com.crediflow.store_credit_management.iam.infrastructure.security;

import com.crediflow.store_credit_management.iam.domain.model.UserRole;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Current User
 * @summary
 * Quién hace la petición, leído del token ya validado. Los controladores lo
 * usan para saber el rol, la tienda y el cliente reales del usuario, sin fiarse
 * de lo que venga en la ruta o el cuerpo.
 *
 * @since 1.0.0
 */
public record CurrentUser(Long id, String username, UserRole role, Long storeId, Long clientId) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                UserRole.valueOf(jwt.getClaimAsString("role")),
                asLong(jwt.getClaim("storeId")),
                asLong(jwt.getClaim("clientId")));
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
