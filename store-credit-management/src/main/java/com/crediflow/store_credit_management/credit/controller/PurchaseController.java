package com.crediflow.store_credit_management.credit.controller;

import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.CreatePurchaseRequest;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.CreditLineResource;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.PurchaseResource;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.PurchaseSummaryResource;
import com.crediflow.store_credit_management.credit.service.CreditLineService;
import com.crediflow.store_credit_management.credit.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase Controller
 * @summary
 * Endpoints REST de compras al crédito y de la línea de crédito de un cliente.
 * Solo traduce HTTP a llamadas de servicio y entidades a Resources: no
 * contiene reglas. Nunca devuelve entidades JPA.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
@RequiredArgsConstructor
@Tag(name = "Purchases", description = "Compras al crédito y línea de crédito de los clientes")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final CreditLineService creditLineService;

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una compra al crédito",
            description = "El sistema toma el precio de crédito de cada producto, calcula intereses y cronograma "
                    + "con la condición vigente del cliente, y valida que quepa en su línea de crédito. "
                    + "Si purchasedAt se omite, se usa la hora actual.")
    public PurchaseResource register(@PathVariable Long storeId,
                                     @Valid @RequestBody CreatePurchaseRequest request) {
        return PurchaseResource.from(purchaseService.register(storeId, request));
    }

    @GetMapping("/purchases")
    @Operation(summary = "Listar compras de la tienda",
            description = "Paginado. El filtro clientId es opcional. No incluye productos ni cuotas: "
                    + "para el detalle usa GET /purchases/{purchaseId}.")
    public PagedModel<PurchaseSummaryResource> list(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long clientId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(
                purchaseService.list(storeId, clientId, pageable).map(PurchaseSummaryResource::from));
    }

    @GetMapping("/purchases/{purchaseId}")
    @Operation(summary = "Consultar una compra con sus productos y su cronograma de cuotas")
    public PurchaseResource get(@PathVariable Long storeId, @PathVariable Long purchaseId) {
        return PurchaseResource.from(purchaseService.get(storeId, purchaseId));
    }

    @GetMapping("/clients/{clientId}/credit-line")
    @Operation(summary = "Consultar la línea de crédito de un cliente",
            description = "Límite, capital usado (cuotas pendientes) y cupo disponible. "
                    + "Responde 404 si el cliente no tiene una condición vigente.")
    public CreditLineResource creditLine(@PathVariable Long storeId, @PathVariable Long clientId) {
        return creditLineService.get(storeId, clientId);
    }
}
