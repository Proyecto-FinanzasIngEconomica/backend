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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Payment
 * @summary
 * Pago de un estado de cuenta completo (el enunciado no admite pagos
 * parciales). Es la raíz del agregado: sus líneas no existen fuera de él.
 *
 * Guarda el desglose real de lo cobrado, con la imputación de la decisión #14
 * (mora, interés compensatorio y capital), y los totales se calculan desde las
 * líneas, no se reciben. Un pago registrado es firme: no se anula.
 *
 * Se referencia al cliente y al estado de cuenta por id.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends AuditableModel {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, updatable = false)
    private Long clientId;

    @Column(nullable = false, updatable = false)
    private Long statementId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    /** Capital pagado: es lo que libera la línea de crédito. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principalPaid;

    /** Interés compensatorio pagado. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal interestPaid;

    /** Mora pagada, con los días reales de atraso. Se cobra aparte del capital. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal lateInterestPaid;

    /** Todo lo cobrado: capital + interés compensatorio + mora. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalPaid;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PaymentLine> lines = new ArrayList<>();

    /** Una cuota pagada, con su desglose ya calculado por el motor. */
    public record LineDraft(Long installmentId,
                            Long purchaseId,
                            int installmentNumber,
                            LocalDate dueDate,
                            int lateDays,
                            BigDecimal principal,
                            BigDecimal interest,
                            BigDecimal lateInterest) {
    }

    /** Registra un pago. Es la única forma de crearlo; los totales salen de las líneas. */
    public static Payment register(Long storeId,
                                   Long clientId,
                                   Long statementId,
                                   LocalDateTime paidAt,
                                   Currency currency,
                                   List<LineDraft> drafts) {
        if (storeId == null || clientId == null || statementId == null || paidAt == null || currency == null) {
            throw new IllegalArgumentException("Faltan datos obligatorios del pago");
        }
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("El pago debe incluir al menos una cuota");
        }

        Payment payment = new Payment();
        payment.storeId = storeId;
        payment.clientId = clientId;
        payment.statementId = statementId;
        payment.paidAt = paidAt;
        payment.currency = currency;

        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal late = BigDecimal.ZERO;
        for (LineDraft draft : drafts) {
            PaymentLine line = PaymentLine.create(payment, draft);
            payment.lines.add(line);
            principal = principal.add(line.getPrincipalPaid());
            interest = interest.add(line.getInterestPaid());
            late = late.add(line.getLateInterestPaid());
        }

        BigDecimal total = principal.add(interest).add(late);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("El total del pago debe ser mayor a cero");
        }
        if (total.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El total del pago excede el máximo permitido");
        }
        payment.principalPaid = principal;
        payment.interestPaid = interest;
        payment.lateInterestPaid = late;
        payment.totalPaid = total;
        return payment;
    }

    /** Vista de solo lectura: las líneas solo cambian al registrar el pago. */
    public List<PaymentLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}