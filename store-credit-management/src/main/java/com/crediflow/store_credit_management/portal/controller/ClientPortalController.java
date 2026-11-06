package com.crediflow.store_credit_management.portal.controller;

import com.crediflow.store_credit_management.catalog.domain.model.ProductStatus;
import com.crediflow.store_credit_management.catalog.dto.ProductDtos.ProductResource;
import com.crediflow.store_credit_management.catalog.service.ProductService;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.ClientResource;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.CreditConditionResource;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.clients.service.CreditConditionService;
import com.crediflow.store_credit_management.credit.dto.PaymentDtos.PaymentResource;
import com.crediflow.store_credit_management.credit.dto.PaymentDtos.PaymentSummaryResource;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.CreditLineResource;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.PurchaseResource;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.PurchaseSummaryResource;
import com.crediflow.store_credit_management.credit.dto.StatementDtos.StatementResource;
import com.crediflow.store_credit_management.credit.dto.StatementDtos.StatementSummaryResource;
import com.crediflow.store_credit_management.credit.service.CreditLineService;
import com.crediflow.store_credit_management.credit.service.PaymentService;
import com.crediflow.store_credit_management.credit.service.PurchaseService;
import com.crediflow.store_credit_management.credit.service.StatementService;
import com.crediflow.store_credit_management.iam.infrastructure.security.CurrentUser;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.AccessForbiddenException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client Portal Controller
 * @summary
 * Lo que un cliente puede consultar de su propia cuenta: perfil, condición y
 * línea de crédito, compras, estados de cuenta y pagos. Es de solo lectura.
 *
 * El storeId y el clientId salen SIEMPRE del token, nunca de la ruta. Para los
 * detalles por id se vuelve a comprobar que el recurso sea del cliente: otro
 * cliente de la misma tienda recibe el mismo 404 que si no existiera.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Client portal", description = "Consultas del cliente sobre su propia cuenta (rol CLIENT)")
public class ClientPortalController {

    private final ClientService clientService;
    private final CreditConditionService creditConditionService;
    private final CreditLineService creditLineService;
    private final PurchaseService purchaseService;
    private final StatementService statementService;
    private final PaymentService paymentService;
    private final ProductService productService;

    @GetMapping("/products")
    @Operation(summary = "Productos activos de mi tienda")
    public PagedModel<ProductResource> products(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        CurrentUser me = requireClient(jwt);
        return new PagedModel<>(productService.list(me.storeId(), ProductStatus.ACTIVE, pageable)
                .map(ProductResource::from));
    }

    @GetMapping("/profile")
    @Operation(summary = "Mis datos")
    public ClientResource profile(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = requireClient(jwt);
        return ClientResource.from(clientService.get(me.storeId(), me.clientId()));
    }

    @GetMapping("/credit-condition")
    @Operation(summary = "Mi condición de crédito vigente")
    public CreditConditionResource creditCondition(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = requireClient(jwt);
        return CreditConditionResource.from(creditConditionService.getActive(me.storeId(), me.clientId()));
    }

    @GetMapping("/credit-line")
    @Operation(summary = "Mi línea de crédito: límite, usado y disponible")
    public CreditLineResource creditLine(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = requireClient(jwt);
        return creditLineService.get(me.storeId(), me.clientId());
    }

    @GetMapping("/purchases")
    @Operation(summary = "Mis compras", description = "Paginado, sin el detalle de productos ni cuotas.")
    public PagedModel<PurchaseSummaryResource> purchases(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        CurrentUser me = requireClient(jwt);
        return new PagedModel<>(purchaseService.list(me.storeId(), me.clientId(), pageable)
                .map(PurchaseSummaryResource::from));
    }

    @GetMapping("/purchases/{purchaseId}")
    @Operation(summary = "Una de mis compras, con productos y cronograma de cuotas")
    public PurchaseResource purchase(@AuthenticationPrincipal Jwt jwt, @PathVariable Long purchaseId) {
        CurrentUser me = requireClient(jwt);
        var purchase = purchaseService.get(me.storeId(), purchaseId);
        if (!me.clientId().equals(purchase.getClientId())) {
            throw (ResourceNotFoundException) ResourceNotFoundException.of("Compra", purchaseId);
        }
        return PurchaseResource.from(purchase);
    }

    @GetMapping("/statements")
    @Operation(summary = "Mis estados de cuenta", description = "Paginado, sin las cuotas.")
    public PagedModel<StatementSummaryResource> statements(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        CurrentUser me = requireClient(jwt);
        return new PagedModel<>(statementService.list(me.storeId(), me.clientId(), pageable)
                .map(StatementSummaryResource::from));
    }

    @GetMapping("/statements/{statementId}")
    @Operation(summary = "Uno de mis estados de cuenta, con sus cuotas")
    public StatementResource statement(@AuthenticationPrincipal Jwt jwt, @PathVariable Long statementId) {
        CurrentUser me = requireClient(jwt);
        var statement = statementService.get(me.storeId(), statementId);
        if (!me.clientId().equals(statement.getClientId())) {
            throw (ResourceNotFoundException) ResourceNotFoundException.of("Estado de cuenta", statementId);
        }
        return StatementResource.from(statement);
    }

    @GetMapping("/payments")
    @Operation(summary = "Mis pagos", description = "Paginado, sin el desglose por cuota.")
    public PagedModel<PaymentSummaryResource> payments(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        CurrentUser me = requireClient(jwt);
        return new PagedModel<>(paymentService.list(me.storeId(), me.clientId(), pageable)
                .map(PaymentSummaryResource::from));
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Uno de mis pagos, con su desglose por cuota")
    public PaymentResource payment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long paymentId) {
        CurrentUser me = requireClient(jwt);
        var payment = paymentService.get(me.storeId(), paymentId);
        if (!me.clientId().equals(payment.getClientId())) {
            throw (ResourceNotFoundException) ResourceNotFoundException.of("Pago", paymentId);
        }
        return PaymentResource.from(payment);
    }

    /** Cinturón y tirantes: la ruta ya exige rol CLIENT, pero sin clientId en el token no hay nada que consultar. */
    private static CurrentUser requireClient(Jwt jwt) {
        CurrentUser me = CurrentUser.from(jwt);
        if (me.clientId() == null || me.storeId() == null) {
            throw new AccessForbiddenException("Esta cuenta no está vinculada a un cliente");
        }
        return me;
    }
}
