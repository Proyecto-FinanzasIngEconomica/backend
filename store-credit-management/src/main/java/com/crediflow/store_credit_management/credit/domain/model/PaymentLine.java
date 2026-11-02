package com.crediflow.store_credit_management.credit.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Payment Line
 * @summary
 * Una cuota dentro de un pago, con su desglose real: capital, interés
 * compensatorio y mora calculada con los días reales de atraso. Es una copia
 * de lo que se cobró, por lo que no cambia aunque otros registros cambien.
 *
 * Solo se crea desde {@link Payment#register}.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLine extends AuditableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(nullable = false, updatable = false)
    private Long installmentId;

    @Column(nullable = false, updatable = false)
    private Long purchaseId;

    @Column(nullable = false, updatable = false)
    private int installmentNumber;

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    /** Días entre el vencimiento y el pago real (0 si se pagó hasta el vencimiento). */
    @Column(nullable = false, updatable = false)
    private int lateDays;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principalPaid;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal interestPaid;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal lateInterestPaid;

    /** Capital + interés compensatorio + mora de esta cuota. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal total;

    static PaymentLine create(Payment payment, Payment.LineDraft draft) {
        if (draft.installmentId() == null || draft.purchaseId() == null || draft.dueDate() == null) {
            throw new IllegalArgumentException("La línea de pago requiere la cuota, la compra y el vencimiento");
        }
        if (draft.installmentNumber() < 1) {
            throw new IllegalArgumentException("El número de cuota debe ser 1 o mayor");
        }
        if (draft.lateDays() < 0) {
            throw new IllegalArgumentException("Los días de atraso no pueden ser negativos");
        }
        if (draft.principal() == null || draft.interest() == null || draft.lateInterest() == null
                || draft.principal().signum() < 0 || draft.interest().signum() < 0
                || draft.lateInterest().signum() < 0) {
            throw new IllegalArgumentException("Los montos de la línea de pago no son válidos");
        }
        BigDecimal late = draft.lateInterest().setScale(2, RoundingMode.HALF_UP);
        if (draft.lateDays() == 0 && late.signum() > 0) {
            throw new IllegalArgumentException("No puede haber mora sin días de atraso");
        }
        PaymentLine line = new PaymentLine();
        line.payment = payment;
        line.installmentId = draft.installmentId();
        line.purchaseId = draft.purchaseId();
        line.installmentNumber = draft.installmentNumber();
        line.dueDate = draft.dueDate();
        line.lateDays = draft.lateDays();
        line.principalPaid = draft.principal().setScale(2, RoundingMode.HALF_UP);
        line.interestPaid = draft.interest().setScale(2, RoundingMode.HALF_UP);
        line.lateInterestPaid = late;
        line.total = line.principalPaid.add(line.interestPaid).add(line.lateInterestPaid);
        return line;
    }
}
