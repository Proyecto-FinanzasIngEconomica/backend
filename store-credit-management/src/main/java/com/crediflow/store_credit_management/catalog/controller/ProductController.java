package com.crediflow.store_credit_management.catalog.controller;


import com.crediflow.store_credit_management.catalog.domain.model.ProductStatus;
import com.crediflow.store_credit_management.catalog.dto.ProductDtos.ProductRequest;
import com.crediflow.store_credit_management.catalog.dto.ProductDtos.ProductResource;
import com.crediflow.store_credit_management.catalog.service.ProductService;
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

/**
 * Product Controller
 * @summary
 * Endpoints REST del catálogo de productos y servicios de una tienda. Solo
 * traduce HTTP a llamadas de servicio y entidades a Resources: no contiene
 * reglas. Nunca devuelve entidades JPA.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Catálogo de productos y servicios de una tienda")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta un producto o servicio",
            description = "Debe poder venderse a fin de mes, en cuotas, o en ambas modalidades. "
                    + "Ambos precios van en la moneda indicada.")
    public ProductResource register(@PathVariable Long storeId,
                                    @Valid @RequestBody ProductRequest request) {
        return ProductResource.from(productService.register(storeId, request));
    }

    @GetMapping
    @Operation(summary = "Listar productos de la tienda",
            description = "Paginado. El filtro status es opcional (ACTIVE o INACTIVE).")
    public PagedModel<ProductResource> list(
            @PathVariable Long storeId,
            @RequestParam(required = false) ProductStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(
                productService.list(storeId, status, pageable).map(ProductResource::from));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Consultar un producto")
    public ProductResource get(@PathVariable Long storeId, @PathVariable Long productId) {
        return ProductResource.from(productService.get(storeId, productId));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Actualizar un producto",
            description = "Reemplaza todos los datos editables. No cambia la tienda. "
                    + "Las compras ya hechas conservan su precio original.")
    public ProductResource update(@PathVariable Long storeId,
                                  @PathVariable Long productId,
                                  @Valid @RequestBody ProductRequest request) {
        return ProductResource.from(productService.update(storeId, productId, request));
    }

    @PatchMapping("/{productId}/deactivate")
    @Operation(summary = "Dar de baja un producto",
            description = "Baja lógica: deja de poder venderse pero se conserva para las compras antiguas.")
    public ProductResource deactivate(@PathVariable Long storeId, @PathVariable Long productId) {
        return ProductResource.from(productService.deactivate(storeId, productId));
    }
}
