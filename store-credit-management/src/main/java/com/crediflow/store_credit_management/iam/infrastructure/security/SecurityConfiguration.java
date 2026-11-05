package com.crediflow.store_credit_management.iam.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Security Configuration
 * @summary
 * Configuración de seguridad: cifrado de contraseñas (BCrypt), firma y
 * validación de JWT (HS256), CORS y la cadena de filtros, sin sesiones (la API
 * es sin estado).
 *
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /** HS256 exige una clave de al menos 256 bits (32 bytes). */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Clave de firma de los JWT. Si falta o es corta, la aplicación NO arranca:
     * es preferible fallar al inicio a emitir tokens firmados con una clave débil.
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${crediflow.security.jwt-secret:}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Falta la clave del JWT: define la variable de entorno CREDIFLOW_JWT_SECRET con al menos "
                            + MIN_SECRET_BYTES + " caracteres (o usa el perfil dev)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${crediflow.cors.allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

        @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AccountStatusJwtConverter jwtConverter,
            JsonSecurityErrorHandlers errorHandlers,
            @Value("${crediflow.security.swagger-public:false}") boolean swaggerPublic,
            @Value("${springdoc.api-docs.path:/v3/api-docs}") String apiDocsPath) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers("/error").permitAll();
                    requests.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
                    if (swaggerPublic) {
                        requests.requestMatchers(
                                apiDocsPath, apiDocsPath + "/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    requests.requestMatchers("/api/v1/auth/**").authenticated();

                    // Administración del sistema
                    requests.requestMatchers("/api/v1/business-types").hasRole("SYSTEM_ADMIN");
                    requests.requestMatchers("/api/v1/stores").hasRole("SYSTEM_ADMIN");
                    requests.requestMatchers(HttpMethod.GET, "/api/v1/stores/{storeId}").access(ownStore(true));
                    requests.requestMatchers("/api/v1/stores/{storeId}").hasRole("SYSTEM_ADMIN");
                    requests.requestMatchers("/api/v1/stores/{storeId}/deactivate").hasRole("SYSTEM_ADMIN");

                    // Dentro de una tienda: el storeId de la ruta debe ser el del token
                    requests.requestMatchers("/api/v1/stores/{storeId}/accounts/**").access(ownStore(true));
                    requests.requestMatchers("/api/v1/stores/{storeId}/**").access(ownStore(false));

                    // Portal del cliente
                    requests.requestMatchers("/api/v1/me/**").hasRole("CLIENT");
                    
                    requests.requestMatchers("/api/v1/audit").hasRole("SYSTEM_ADMIN");

                    requests.anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers));
        return http.build();
    }

    /**
     * Regla de aislamiento entre tiendas: el STORE_ADMIN solo entra si el
     * storeId de la ruta es exactamente el de su token. Con allowSystemAdmin,
     * el SYSTEM_ADMIN también pasa (cualquier tienda).
     */
    private static AuthorizationManager<RequestAuthorizationContext> ownStore(boolean allowSystemAdmin) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }
            boolean systemAdmin = hasAuthority(auth, "ROLE_SYSTEM_ADMIN");
            if (systemAdmin) {
                return new AuthorizationDecision(allowSystemAdmin);
            }
            if (!hasAuthority(auth, "ROLE_STORE_ADMIN")) {
                return new AuthorizationDecision(false);
            }
            Object tokenStoreId = token.getToken().getClaim("storeId");
            String pathStoreId = context.getVariables().get("storeId");
            return new AuthorizationDecision(tokenStoreId != null && tokenStoreId.toString().equals(pathStoreId));
        };
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}