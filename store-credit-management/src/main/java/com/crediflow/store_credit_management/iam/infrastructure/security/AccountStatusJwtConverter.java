package com.crediflow.store_credit_management.iam.infrastructure.security;

import com.crediflow.store_credit_management.iam.domain.model.UserAccount;
import com.crediflow.store_credit_management.iam.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Account Status Jwt Converter
 * @summary
 * Convierte un token ya verificado en la identidad de la petición y, en cada
 * petición, comprueba que la cuenta siga ACTIVA. Así una baja o un bloqueo
 * cortan el acceso al instante, sin esperar a que venza el token de 8 horas.
 * El rol sale de la base de datos, no del token.
 *
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
class AccountStatusJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAccountRepository repository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long accountId;
        try {
            accountId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Token inválido");
        }
        UserAccount account = repository.findById(accountId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BadCredentialsException("La cuenta ya no está activa"));
        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())),
                jwt.getSubject());
    }
}
