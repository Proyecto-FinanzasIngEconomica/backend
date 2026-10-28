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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Statement Line
 * @summary
 * Una cuota dentro de un estado de cuenta. Guarda una COPIA de los datos de la
 * cuota y de su compra al momento de emitir el estado, para que el documento
 * no cambie aunque después se paguen o se modifiquen otros registros.
 *
 * Solo se crea desde {@link Statement#register}.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatementLine extends AuditableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false, updatable = false)
    private Statement statement;

    /** La cuota a la que corresponde esta línea (el pago la marcará como pagada). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false, updatable = false)
    private PurchaseInstallment installment;

    @Column(nullable = false, updatable = false)
    private Long purchaseId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    @Column(nullable = false, updatable = false)
    private int installmentNumber;

    /** Total de cuotas de la compra (para mostrar "2 de 3"). */
    @Column(nullable = false, updatable = false)
    private int installmentCount;

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal principalPart;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal interestPart;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    /** Cuota vencida antes de la fecha de pago del ciclo: viene arrastrada de un ciclo anterior. */
    @Column(nullable = false, updatable = false)
    private boolean overdue;

    /** Días de atraso proyectados hasta la fecha de pago del ciclo (0 si no está vencida). */
    @Column(nullable = false, updatable = false)
    private int lateDays;

    /** Mora proyectada hasta la fecha de pago del ciclo. El pago real la recalcula con los días reales. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal lateInterest;

    static StatementLine create(Statement statement,
                                PurchaseInstallment installment,
                                boolean overdue,
                                int lateDays,
                                BigDecimal lateInterest) {
        Purchase purchase = installment.getPurchase();
        StatementLine line = new StatementLine();
        line.statement = statement;
        line.installment = installment;
        line.purchaseId = purchase.getId();
        line.purchasedAt = purchase.getPurchasedAt();
        line.installmentNumber = installment.getInstallmentNumber();
        line.installmentCount = purchase.getInstallmentCount();
        line.dueDate = installment.getDueDate();
        line.principalPart = installment.getPrincipalPart();
        line.interestPart = installment.getInterestPart();
        line.amount = installment.getAmount();
        line.overdue = overdue;
        line.lateDays = lateDays;
        line.lateInterest = lateInterest;
        return line;
    }
}
