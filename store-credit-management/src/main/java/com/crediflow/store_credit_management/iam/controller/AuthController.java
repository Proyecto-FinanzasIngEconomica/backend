package com.crediflow.store_credit_management.iam.controller;

import com.crediflow.store_credit_management.iam.dto.AuthDtos.LoginRequest;
import com.crediflow.store_credit_management.iam.dto.AuthDtos.LoginResponse;
import com.crediflow.store_credit_management.iam.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth Controller
 * @summary
 * Inicio de sesión. Solo traduce HTTP a una llamada de servicio: no contiene reglas.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Inicio de sesión")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un JWT de 8 horas con el rol y la tienda del usuario. La cuenta se bloquea "
                    + "tras 5 intentos fallidos seguidos. Usa el token en el botón Authorize de Swagger.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return LoginResponse.from(
                result.token().value(), result.token().expiresAt(), result.account(), result.clientId());
    }
}
