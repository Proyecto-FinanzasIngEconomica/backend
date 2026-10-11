package com.crediflow.store_credit_management.clients.controller;

import com.crediflow.store_credit_management.clients.domain.model.ClientStatus;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.ClientResource;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.CreateClientRequest;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.CreateCreditConditionRequest;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.CreditConditionResource;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.UpdateClientRequest;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.clients.service.CreditConditionService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Client Controller
 * @summary
 * Endpoints REST de clientes y de sus condiciones de crédito. Solo traduce
 * HTTP a llamadas de servicio y entidades a Resources: no contiene reglas.
 * Nunca devuelve entidades JPA.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Clientes de una tienda y sus condiciones de crédito")
public class ClientController {

    private final ClientService clientService;
    private final CreditConditionService creditConditionService;

    // ------------------------------------------------------------------
    // Clientes
    // ------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta un cliente",
            description = "El documento no puede repetirse dentro de la misma tienda.")
    public ClientResource register(@PathVariable Long storeId,
                                   @Valid @RequestBody CreateClientRequest request) {
        return ClientResource.from(clientService.register(storeId, request));
    }

    @GetMapping
    @Operation(summary = "Listar clientes de la tienda",
            description = "Paginado. El filtro status es opcional (ACTIVE o INACTIVE).")
    public PagedModel<ClientResource> list(
            @PathVariable Long storeId,
            @RequestParam(required = false) ClientStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(
                clientService.list(storeId, status, pageable).map(ClientResource::from));
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Consultar un cliente")
    public ClientResource get(@PathVariable Long storeId, @PathVariable Long clientId) {
        return ClientResource.from(clientService.get(storeId, clientId));
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "Actualizar los datos generales de un cliente",
            description = "No permite cambiar el documento ni la tienda.")
    public ClientResource update(@PathVariable Long storeId,
                                 @PathVariable Long clientId,
                                 @Valid @RequestBody UpdateClientRequest request) {
        return ClientResource.from(clientService.update(storeId, clientId, request));
    }

    @PatchMapping("/{clientId}/deactivate")
    @Operation(summary = "Dar de baja a un cliente",
            description = "Baja lógica: el historial se conserva. Se rechaza si tiene saldo pendiente.")
    public ClientResource deactivate(@PathVariable Long storeId, @PathVariable Long clientId) {
        return ClientResource.from(clientService.deactivate(storeId, clientId));
    }

    // ------------------------------------------------------------------
    // Condiciones de crédito
    // ------------------------------------------------------------------

    @PostMapping("/{clientId}/credit-conditions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una condición de crédito",
            description = "Si el cliente ya tenía una vigente, se cierra y esta pasa a ser la nueva. "
                    + "La tasa se convierte a TEA al guardarla.")
    public CreditConditionResource registerCondition(
            @PathVariable Long storeId,
            @PathVariable Long clientId,
            @Valid @RequestBody CreateCreditConditionRequest request) {
        return CreditConditionResource.from(
                creditConditionService.register(storeId, clientId, request));
    }

    @GetMapping("/{clientId}/credit-conditions/active")
    @Operation(summary = "Consultar la condición de crédito vigente")
    public CreditConditionResource getActiveCondition(@PathVariable Long storeId,
                                                      @PathVariable Long clientId) {
        return CreditConditionResource.from(creditConditionService.getActive(storeId, clientId));
    }

    @GetMapping("/{clientId}/credit-conditions")
    @Operation(summary = "Historial de condiciones de crédito",
            description = "De la más reciente a la más antigua.")
    public List<CreditConditionResource> conditionHistory(@PathVariable Long storeId,
                                                          @PathVariable Long clientId) {
        return creditConditionService.history(storeId, clientId).stream()
                .map(CreditConditionResource::from)
                .toList();
    }
}
