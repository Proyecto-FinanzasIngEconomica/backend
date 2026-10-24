package com.crediflow.store_credit_management.credit.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Purchase Installment
 * @summary
 * Una cuota por pagar. Tanto las compras en cuotas como las de fin de mes
 * generan cuotas (la de fin de mes tiene una sola), así que estados de cuenta,
 * pagos y mora trabajan con un único tipo de fila.
 *
 * Su parte de capital es la que consume la línea de crédito mientras la cuota
 * está pendiente.
 *
 * Solo se crea desde {@link Purchase#register}. El pago se agrega junto con
 * el módulo de pagos.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseInstallment extends AuditableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(nullable = false, updatable = false)
    private int installmentNumber;

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    /** Monto total de la cuota = capital + interés. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    /** Parte de capital: lo que esta cuota libera de la línea al pagarse. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principalPart;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal interestPart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InstallmentStatus status;

    @Column
    private LocalDateTime paidAt;

    static PurchaseInstallment create(Purchase purchase,
                                      int installmentNumber,
                                      LocalDate dueDate,
                                      BigDecimal amount,
                                      BigDecimal principalPart,
                                      BigDecimal interestPart) {
        if (installmentNumber < 1) {
            throw new IllegalArgumentException("El número de cuota debe ser 1 o mayor");
        }
        if (dueDate == null || amount == null || principalPart == null || interestPart == null) {
            throw new IllegalArgumentException("La cuota requiere fecha de vencimiento y sus montos");
        }
        if (amount.signum() <= 0 || principalPart.signum() < 0 || interestPart.signum() < 0) {
            throw new IllegalArgumentException("Los montos de la cuota no son válidos");
        }
        BigDecimal total = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal principal = principalPart.setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = interestPart.setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(principal.add(interest)) != 0) {
            throw new IllegalArgumentException("La cuota debe ser igual a su capital más su interés");
        }
        PurchaseInstallment installment = new PurchaseInstallment();
        installment.purchase = purchase;
        installment.installmentNumber = installmentNumber;
        installment.dueDate = dueDate;
        installment.amount = total;
        installment.principalPart = principal;
        installment.interestPart = interest;
        installment.status = InstallmentStatus.PENDING;
        return installment;
    }

    public boolean isPending() {
        return status == InstallmentStatus.PENDING;
    }
}