package com.crediflow.store_credit_management.iam.service;

import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Token Service
 * @summary
 * Emite los JWT de acceso. El token identifica al usuario y lleva su rol y su
 * tienda (y su cliente, si es un CLIENT), de modo que cada petición se pueda
 * autorizar y filtrar por tienda sin consultar la base de datos.
 *
 * Es un token de acceso único (sin refresh): al vencer, se vuelve a iniciar sesión.
 *
 * @since 1.0.0
 */
@Service
public class TokenService {

    private static final String ISSUER = "crediflow";

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    public TokenService(JwtEncoder jwtEncoder,
                        @Value("${crediflow.security.jwt-expiration-hours:8}") long expirationHours) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = Duration.ofHours(expirationHours);
    }

    /** Token ya firmado y el instante en que vence. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    /**
     * Emite un token para la cuenta. {@code clientId} solo se informa para el
     * rol CLIENT (el registro de cliente vinculado a su cuenta).
     */
    public IssuedToken issue(UserAccount account, Long clientId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(account.getId()))
                .claim("username", account.getUsername())
                .claim("role", account.getRole().name());
        if (account.getStoreId() != null) {
            claims.claim("storeId", account.getStoreId());
        }
        if (clientId != null) {
            claims.claim("clientId", clientId);
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
