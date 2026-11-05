package com.crediflow.store_credit_management.iam.controller;

import com.crediflow.store_credit_management.iam.dto.AuthDtos.ChangePasswordRequest;
import com.crediflow.store_credit_management.iam.dto.AuthDtos.LoginRequest;
import com.crediflow.store_credit_management.iam.dto.AuthDtos.LoginResponse;
import com.crediflow.store_credit_management.iam.dto.AuthDtos.UserResource;
import com.crediflow.store_credit_management.iam.infrastructure.security.CurrentUser;
import com.crediflow.store_credit_management.iam.service.AccountService;
import com.crediflow.store_credit_management.iam.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final AccountService accountService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un JWT de 8 horas con el rol y la tienda del usuario. La cuenta se bloquea "
                    + "tras 5 intentos fallidos seguidos. Usa el token en el botón Authorize de Swagger.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return LoginResponse.from(
                result.token().value(), result.token().expiresAt(), result.account(), result.clientId());
    }

        @GetMapping("/me")
    @Operation(summary = "Quién soy", description = "Datos del usuario del token, sin consultar contraseñas.")
    public UserResource me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return new UserResource(user.id(), user.username(), user.role(), user.storeId(), user.clientId());
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar mi contraseña")
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changeOwnPassword(CurrentUser.from(jwt).id(), request.currentPassword(), request.newPassword());
    }
}
