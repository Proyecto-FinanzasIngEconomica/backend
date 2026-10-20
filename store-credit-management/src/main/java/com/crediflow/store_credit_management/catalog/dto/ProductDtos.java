package com.crediflow.store_credit_management.catalog.dto;

import com.crediflow.store_credit_management.catalog.domain.model.Product;
import com.crediflow.store_credit_management.catalog.domain.model.ProductStatus;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Product DTOs
 * @summary
 * Objetos de entrada (Request) y salida (Resource) del módulo catalog.
 * Validan el formato; las reglas de negocio (al menos una modalidad, URL
 * http/https, tienda) las valida la entidad y el servicio.
 *
 * @since 1.0.0
 */
public final class ProductDtos {

    private ProductDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    /**
     * Sirve para crear (POST) y para editar (PUT): todos los datos editables
     * son los mismos, y la tienda sale de la ruta, no del cuerpo.
     */
    public record ProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 300) String description,
            @Size(max = 100) String supplier,
            @Size(max = 100) String brand,

            @Schema(example = "kg")
            @NotBlank @Size(max = 30) String unitOfMeasure,

            @Schema(description = "Solo la dirección de la imagen (http/https); no se suben archivos",
                    example = "https://midominio.com/img/manzana.jpg")
            @Size(max = 255) String imageUrl,

            @Schema(example = "4.50")
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
            BigDecimal cashPrice,

            @Schema(example = "5.00")
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
            BigDecimal creditPrice,

            @Schema(description = "Moneda de AMBOS precios", example = "PEN")
            @NotNull Currency currency,

            @Schema(description = "Se puede vender con pago único a fin de mes")
            @NotNull Boolean endOfMonthAllowed,

            @Schema(description = "Se puede vender en cuotas. Al menos una de las dos modalidades debe ser true")
            @NotNull Boolean installmentsAllowed
    ) {
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record ProductResource(
            Long id,
            String name,
            String description,
            String supplier,
            String brand,
            String unitOfMeasure,
            String imageUrl,
            BigDecimal cashPrice,
            BigDecimal creditPrice,
            Currency currency,
            boolean endOfMonthAllowed,
            boolean installmentsAllowed,
            ProductStatus status
    ) {
        public static ProductResource from(Product product) {
            return new ProductResource(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getSupplier(),
                    product.getBrand(),
                    product.getUnitOfMeasure(),
                    product.getImageUrl(),
                    product.getCashPrice(),
                    product.getCreditPrice(),
                    product.getCurrency(),
                    product.isEndOfMonthAllowed(),
                    product.isInstallmentsAllowed(),
                    product.getStatus());
        }
    }
}
