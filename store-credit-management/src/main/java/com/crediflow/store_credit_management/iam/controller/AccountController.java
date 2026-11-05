package com.crediflow.store_credit_management.iam.controller;

import com.crediflow.store_credit_management.iam.dto.AccountDtos.AccountResource;
import com.crediflow.store_credit_management.iam.dto.AccountDtos.CreateAccountRequest;
import com.crediflow.store_credit_management.iam.dto.AccountDtos.ResetPasswordRequest;
import com.crediflow.store_credit_management.iam.infrastructure.security.CurrentUser;
import com.crediflow.store_credit_management.iam.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account Controller
 * @summary
 * Gestión de cuentas de una tienda. Solo traduce HTTP a llamadas de servicio.
 * Quién puede llamarlo lo fija la parte 5.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Cuentas de acceso de una tienda")
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/clients/{clientId}/account")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear la cuenta de acceso de un cliente",
            description = "Un cliente tiene como máximo una cuenta. El cliente debe estar activo.")
    public AccountResource createClientAccount(@PathVariable Long storeId,
                                               @PathVariable Long clientId,
                                               @Valid @RequestBody CreateAccountRequest request) {
        return AccountResource.from(
                accountService.createClientAccount(storeId, clientId, request.username(), request.password()));
    }

    @GetMapping("/accounts")
    @Operation(summary = "Listar las cuentas que puedes gestionar",
            description = "El administrador de tienda ve las cuentas de clientes; el administrador del sistema, las de administradores de tienda.")
    public PagedModel<AccountResource> list(@PathVariable Long storeId,
                                            @AuthenticationPrincipal Jwt jwt,
                                            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(accountService.list(storeId, CurrentUser.from(jwt).role(), pageable)
                .map(AccountResource::from));
    }

    @PatchMapping("/accounts/{accountId}/unblock")
    @Operation(summary = "Desbloquear una cuenta bloqueada por intentos fallidos")
    public AccountResource unblock(@PathVariable Long storeId, @PathVariable Long accountId,
                                   @AuthenticationPrincipal Jwt jwt) {
        return AccountResource.from(accountService.unblock(storeId, accountId, CurrentUser.from(jwt).role()));
    }

    @PatchMapping("/accounts/{accountId}/deactivate")
    @Operation(summary = "Dar de baja una cuenta",
            description = "No se puede dar de baja al único administrador activo de la tienda.")
    public AccountResource deactivate(@PathVariable Long storeId, @PathVariable Long accountId,
                                      @AuthenticationPrincipal Jwt jwt) {
        return AccountResource.from(accountService.deactivate(storeId, accountId, CurrentUser.from(jwt).role()));
    }

    @PatchMapping("/accounts/{accountId}/password")
    @Operation(summary = "Restablecer la contraseña de una cuenta")
    public AccountResource resetPassword(@PathVariable Long storeId, @PathVariable Long accountId,
                                         @Valid @RequestBody ResetPasswordRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return AccountResource.from(accountService.resetPassword(
                storeId, accountId, request.newPassword(), CurrentUser.from(jwt).role()));
    }
}
