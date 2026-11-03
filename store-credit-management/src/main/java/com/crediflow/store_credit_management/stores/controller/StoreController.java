package com.crediflow.store_credit_management.stores.controller;

import com.crediflow.store_credit_management.stores.domain.model.StoreStatus;
import com.crediflow.store_credit_management.stores.dto.StoreDtos.BusinessTypeResource;
import com.crediflow.store_credit_management.stores.dto.StoreDtos.CreateStoreRequest;
import com.crediflow.store_credit_management.stores.dto.StoreDtos.StoreResource;
import com.crediflow.store_credit_management.stores.dto.StoreDtos.UpdateStoreRequest;
import com.crediflow.store_credit_management.stores.service.StoreService;
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
 * Store Controller
 * @summary
 * Endpoints REST de tiendas y del catálogo de giros. Solo traduce HTTP a
 * llamadas de servicio y entidades a Resources: no contiene reglas. Las
 * operaciones sobre tiendas son del administrador del sistema (lo exigirá IAM).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Stores", description = "Tiendas (inquilinos) y catálogo de giros de negocio")
public class StoreController {

    private final StoreService storeService;

    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta una tienda",
            description = "El RUC (11 dígitos) no puede repetirse y no se puede cambiar después.")
    public StoreResource register(@Valid @RequestBody CreateStoreRequest request) {
        return StoreResource.from(storeService.register(request));
    }

    @GetMapping("/stores")
    @Operation(summary = "Listar tiendas",
            description = "Paginado. El filtro status es opcional (ACTIVE o INACTIVE).")
    public PagedModel<StoreResource> list(
            @RequestParam(required = false) StoreStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(storeService.list(status, pageable).map(StoreResource::from));
    }

    @GetMapping("/stores/{storeId}")
    @Operation(summary = "Consultar una tienda")
    public StoreResource get(@PathVariable Long storeId) {
        return StoreResource.from(storeService.get(storeId));
    }

    @PutMapping("/stores/{storeId}")
    @Operation(summary = "Actualizar los datos de una tienda",
            description = "No permite cambiar el RUC.")
    public StoreResource update(@PathVariable Long storeId,
                                @Valid @RequestBody UpdateStoreRequest request) {
        return StoreResource.from(storeService.update(storeId, request));
    }

    @PatchMapping("/stores/{storeId}/deactivate")
    @Operation(summary = "Dar de baja una tienda",
            description = "Baja lógica: se conservan todos sus datos. Se rechaza si tiene cuotas pendientes de cobro.")
    public StoreResource deactivate(@PathVariable Long storeId) {
        return StoreResource.from(storeService.deactivate(storeId));
    }

    @GetMapping("/business-types")
    @Operation(summary = "Catálogo de giros de negocio",
            description = "Para llenar la lista desplegable al crear o editar una tienda.")
    public List<BusinessTypeResource> businessTypes() {
        return storeService.listBusinessTypes().stream().map(BusinessTypeResource::from).toList();
    }
}
