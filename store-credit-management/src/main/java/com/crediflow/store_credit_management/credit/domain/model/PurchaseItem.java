package com.crediflow.store_credit_management.credit.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * Purchase Item
 * @summary
 * Línea de una compra: un producto con su cantidad. Guarda una COPIA del
 * nombre, la unidad y el precio al momento de la venta, así que editar o dar
 * de baja el producto después no altera compras ya hechas. El producto se
 * referencia solo por su id (credit no navega entidades de catalog).
 *
 * Solo se crea desde {@link Purchase#register}.
 */
@Entity 
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseItem extends AuditableModel {
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999.999");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, length = 150)
    private String productName;

    @Column(nullable = false, length = 30)
    private String unitOfMeasure;

    /** Admite decimales (1.5 kg) hasta 3 posiciones. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    /** Precio de crédito unitario vigente al momento de la venta. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /**
     * Fórmula única del subtotal. La usa también el servicio para conocer el
     * total de la compra antes de crearla, así ambos cálculos no pueden diferir.
     */
    public static BigDecimal subtotalOf(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.setScale(3, RoundingMode.HALF_UP)
                .multiply(unitPrice.setScale(2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    static PurchaseItem create(Purchase purchase,
                               Long productId,
                               String productName,
                               String unitOfMeasure,
                               BigDecimal quantity,
                               BigDecimal unitPrice) {
        if (productId == null) {
            throw new IllegalArgumentException("El producto es obligatorio");
        }
        if (productName == null || productName.isBlank() || unitOfMeasure == null || unitOfMeasure.isBlank()) {
            throw new IllegalArgumentException("El nombre y la unidad del producto son obligatorios");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
        if (quantity.stripTrailingZeros().scale() > 3 || quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException("La cantidad admite hasta 3 decimales y un máximo de 999999999.999");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("El precio unitario no puede ser negativo");
        }
        PurchaseItem item = new PurchaseItem();
        item.purchase = purchase;
        item.productId = productId;
        item.productName = productName.trim();
        item.unitOfMeasure = unitOfMeasure.trim();
        item.quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        item.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        item.subtotal = subtotalOf(quantity, unitPrice);
        return item;
    }
}
