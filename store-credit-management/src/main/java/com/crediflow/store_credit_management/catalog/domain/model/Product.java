package com.crediflow.store_credit_management.catalog.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Product
 * @summary
 * Producto o servicio que una tienda vende. No se controla inventario ni stock
 * (fuera de alcance), por eso no hay existencias: solo datos de identificación,
 * precios y modalidades de venta.
 *
 * Tiene dos precios: el de contado y el de lista para crédito. Las compras
 * usan el precio de crédito y guardan una copia del nombre y del precio al
 * momento de la venta, así que cambiar el precio aquí NO altera compras ya
 * hechas.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends AuditableModel {

    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    /** Tienda dueña del producto. Base del aislamiento multi-tienda. */
    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(length = 100)
    private String supplier;

    @Column(length = 100)
    private String brand;

    /** kg, unidad, sesión, litro… Texto libre porque cada giro usa las suyas. */
    @Column(nullable = false, length = 30)
    private String unitOfMeasure;

    /** Dirección de la imagen. La imagen se sube por fuera; aquí solo se guarda la URL. */
    @Column(length = 255)
    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal creditPrice;

    /** Moneda en que están expresados AMBOS precios. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    /** Puede venderse con pago único a fin de mes. */
    @Column(nullable = false)
    private boolean endOfMonthAllowed;

    /** Puede venderse en cuotas mensuales. */
    @Column(nullable = false)
    private boolean installmentsAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status;

    @Column
    private LocalDateTime deactivatedAt;

    /**
     * Da de alta un producto. Es la única forma de crearlo, así nunca existe
     * uno con datos obligatorios vacíos o precios inválidos.
     */
    public static Product register(Long storeId,
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
                                   boolean installmentsAllowed) {
        if (storeId == null) {
            throw new IllegalArgumentException("La tienda es obligatoria");
        }
        Product product = new Product();
        product.storeId = storeId;
        product.status = ProductStatus.ACTIVE;
        product.updateDetails(name, description, supplier, brand, unitOfMeasure, imageUrl,
                cashPrice, creditPrice, currency, endOfMonthAllowed, installmentsAllowed);
        return product;
    }

    /** Actualiza todos los datos editables (la tienda no cambia nunca). */
    public void updateDetails(String name,
                              String description,
                              String supplier,
                              String brand,
                              String unitOfMeasure,
                              String imageUrl,
                              BigDecimal cashPrice,
                              BigDecimal creditPrice,
                              Currency currency,
                              boolean endOfMonthAllowed,
                              boolean installmentsAllowed) {
        if (currency == null) {
            throw new IllegalArgumentException("La moneda es obligatoria");
        }
        if (!endOfMonthAllowed && !installmentsAllowed) {
            throw new IllegalArgumentException(
                    "El producto debe poder venderse a fin de mes, en cuotas, o en ambas modalidades");
        }
        this.name = requireText(name, 150, "El nombre");
        this.description = optionalText(description, 300, "La descripción");
        this.supplier = optionalText(supplier, 100, "El proveedor");
        this.brand = optionalText(brand, 100, "La marca");
        this.unitOfMeasure = requireText(unitOfMeasure, 30, "La unidad de medida");
        this.imageUrl = optionalImageUrl(imageUrl);
        this.cashPrice = requirePrice(cashPrice, "El precio al contado");
        this.creditPrice = requirePrice(creditPrice, "El precio a crédito");
        this.currency = currency;
        this.endOfMonthAllowed = endOfMonthAllowed;
        this.installmentsAllowed = installmentsAllowed;
    }

    /** Baja lógica: el producto deja de poder venderse pero no se borra. */
    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
        this.deactivatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == ProductStatus.ACTIVE;
    }

    private static String requireText(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new IllegalArgumentException(
                    field + " es obligatorio y no puede superar " + max + " caracteres");
        }
        return text;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new IllegalArgumentException(field + " no puede superar " + max + " caracteres");
        }
        return text;
    }

    private static String optionalImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String url = value.trim();
        if (url.length() > 255) {
            throw new IllegalArgumentException("La URL de la imagen no puede superar 255 caracteres");
        }
        if (!url.matches("(?i)^https?://\\S+$")) {
            throw new IllegalArgumentException("La URL de la imagen debe comenzar con http:// o https://");
        }
        return url;
    }

    private static BigDecimal requirePrice(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        BigDecimal price = value.setScale(2, RoundingMode.HALF_UP);
        if (price.signum() < 0) {
            throw new IllegalArgumentException(field + " no puede ser negativo");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(field + " supera el máximo permitido");
        }
        return price;
    }
}
