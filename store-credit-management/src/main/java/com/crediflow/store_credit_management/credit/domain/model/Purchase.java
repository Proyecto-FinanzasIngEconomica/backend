package com.crediflow.store_credit_management.credit.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Purchase
 * @summary
 * Compra al crédito de un cliente. Es la raíz del agregado: sus productos y
 * sus cuotas no existen fuera de ella, y se crean y guardan juntos.
 *
 * Guarda una COPIA de las tasas del momento de la compra, así que cambiar la
 * condición de crédito después no altera esta deuda. El cliente y la condición
 * se referencian por id, para no acoplar este módulo a las entidades de clients.
 *
 * Los totales no se reciben: se calculan desde los productos y las cuotas, de
 * modo que nunca pueden contradecirse.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase extends AuditableModel {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, updatable = false)
    private Long clientId;

    /** Condición vigente al comprar. Solo para auditoría. */
    @Column(nullable = false, updatable = false)
    private Long creditConditionId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PurchaseModality modality;

    /** 1 para fin de mes; N para cuotas. */
    @Column(nullable = false, updatable = false)
    private int installmentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    /** TEA compensatoria vigente al comprar (copia). */
    @Column(nullable = false, precision = 12, scale = 9, updatable = false)
    private BigDecimal tea;

    /** TEA moratoria vigente al comprar (copia). */
    @Column(nullable = false, precision = 12, scale = 9, updatable = false)
    private BigDecimal lateTea;

    /** Fecha de corte del ciclo al que pertenece la compra. */
    @Column(nullable = false, updatable = false)
    private LocalDate cycleCutoffDate;

    /** Fecha de pago del ciclo: vence la cuota de fin de mes o la primera cuota. */
    @Column(nullable = false, updatable = false)
    private LocalDate cyclePaymentDate;

    /**
     * Días entre la compra y la fecha de pago del ciclo. En cuotas son los
     * días de gracia que se capitalizan; en fin de mes son los que generan interés.
     */
    @Column(nullable = false, updatable = false)
    private int graceDays;

    /** Suma de los productos (cantidad por precio de crédito). */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal itemsTotal;

    /** Capital que se financia (en cuotas incluye el interés de gracia capitalizado). */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal financedAmount;

    /** Todo el interés de la compra: totalToPay - itemsTotal. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalInterest;

    /** Suma de todas las cuotas. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalToPay;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<PurchaseInstallment> installments = new ArrayList<>();

    /** Datos de un producto comprado, ya con la copia de nombre, unidad y precio. */
    public record ItemDraft(Long productId,
                            String productName,
                            String unitOfMeasure,
                            BigDecimal quantity,
                            BigDecimal unitPrice) {
    }

    /** Datos de una cuota, tal como salen del motor financiero. */
    public record InstallmentDraft(int number,
                                   LocalDate dueDate,
                                   BigDecimal amount,
                                   BigDecimal principalPart,
                                   BigDecimal interestPart) {
    }

    /**
     * Registra una compra. Es la única forma de crearla, y verifica que todo
     * sea coherente: modalidad vs. cuotas, numeración, totales.
     */
    public static Purchase register(Long storeId,
                                    Long clientId,
                                    Long creditConditionId,
                                    LocalDateTime purchasedAt,
                                    PurchaseModality modality,
                                    int installmentCount,
                                    Currency currency,
                                    BigDecimal tea,
                                    BigDecimal lateTea,
                                    LocalDate cycleCutoffDate,
                                    LocalDate cyclePaymentDate,
                                    int graceDays,
                                    List<ItemDraft> items,
                                    List<InstallmentDraft> installments) {
        if (storeId == null || clientId == null || creditConditionId == null) {
            throw new IllegalArgumentException("La tienda, el cliente y la condición son obligatorios");
        }
        if (purchasedAt == null || modality == null || currency == null || tea == null || lateTea == null
                || cycleCutoffDate == null || cyclePaymentDate == null) {
            throw new IllegalArgumentException("Faltan datos obligatorios de la compra");
        }
        if (graceDays < 0) {
            throw new IllegalArgumentException("Los días de gracia no pueden ser negativos");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La compra debe tener al menos un producto");
        }
        if (installments == null || installments.isEmpty()) {
            throw new IllegalArgumentException("La compra debe tener al menos una cuota");
        }
        if (modality == PurchaseModality.END_OF_MONTH && installmentCount != 1) {
            throw new IllegalArgumentException("Una compra a fin de mes tiene una sola cuota");
        }
        if (installmentCount < 1 || installments.size() != installmentCount) {
            throw new IllegalArgumentException("El número de cuotas no coincide con el cronograma");
        }
        if (installments.get(0).dueDate().isBefore(purchasedAt.toLocalDate())) {
            throw new IllegalArgumentException("La primera cuota no puede vencer antes de la compra");
        }

        Purchase purchase = new Purchase();
        purchase.storeId = storeId;
        purchase.clientId = clientId;
        purchase.creditConditionId = creditConditionId;
        purchase.purchasedAt = purchasedAt;
        purchase.modality = modality;
        purchase.installmentCount = installmentCount;
        purchase.currency = currency;
        purchase.tea = tea;
        purchase.lateTea = lateTea;
        purchase.cycleCutoffDate = cycleCutoffDate;
        purchase.cyclePaymentDate = cyclePaymentDate;
        purchase.graceDays = graceDays;

        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ItemDraft draft : items) {
            PurchaseItem item = PurchaseItem.create(purchase, draft.productId(), draft.productName(),
                    draft.unitOfMeasure(), draft.quantity(), draft.unitPrice());
            purchase.items.add(item);
            itemsTotal = itemsTotal.add(item.getSubtotal());
        }

        BigDecimal totalToPay = BigDecimal.ZERO;
        BigDecimal financed = BigDecimal.ZERO;
        for (int index = 0; index < installments.size(); index++) {
            InstallmentDraft draft = installments.get(index);
            if (draft.number() != index + 1) {
                throw new IllegalArgumentException("Las cuotas deben numerarse 1, 2, 3... en orden");
            }
            PurchaseInstallment installment = PurchaseInstallment.create(purchase, draft.number(),
                    draft.dueDate(), draft.amount(), draft.principalPart(), draft.interestPart());
            purchase.installments.add(installment);
            totalToPay = totalToPay.add(installment.getAmount());
            financed = financed.add(installment.getPrincipalPart());
        }

        if (itemsTotal.compareTo(MAX_AMOUNT) > 0 || totalToPay.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El monto de la compra excede el máximo permitido");
        }
        if (financed.compareTo(itemsTotal) < 0) {
            throw new IllegalArgumentException("El capital financiado no puede ser menor al valor de los productos");
        }
        if (modality == PurchaseModality.END_OF_MONTH && financed.compareTo(itemsTotal) != 0) {
            throw new IllegalArgumentException("En fin de mes el capital debe ser igual al valor de los productos");
        }

        purchase.itemsTotal = itemsTotal.setScale(2, RoundingMode.HALF_UP);
        purchase.financedAmount = financed;
        purchase.totalToPay = totalToPay;
        purchase.totalInterest = totalToPay.subtract(purchase.itemsTotal);
        return purchase;
    }

    /** Vista de solo lectura: la lista solo se modifica creando la compra. */
    public List<PurchaseItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<PurchaseInstallment> getInstallments() {
        return Collections.unmodifiableList(installments);
    }
}
