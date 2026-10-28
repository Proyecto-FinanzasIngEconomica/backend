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
 * Statement
 * @summary
 * Estado de cuenta de un cliente para un ciclo de facturación: lo que debe
 * pagar en la fecha de pago, con el detalle de cuotas ordenado por fecha de
 * compra. Es la raíz del agregado: sus líneas no existen fuera de ella.
 *
 * Incluye las cuotas que vencen en el ciclo y las vencidas arrastradas de
 * ciclos anteriores (decisión #10), con la mora proyectada a la fecha de pago.
 * Los totales se calculan desde las líneas, no se reciben.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Statement extends AuditableModel {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, updatable = false)
    private Long clientId;

    /** Corte del ciclo. Junto con el cliente identifica al estado: no hay dos iguales. */
    @Column(nullable = false, updatable = false)
    private LocalDate cycleCutoffDate;

    @Column(nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatementStatus status;

    /** Cuotas que vencen en este ciclo (capital más interés compensatorio). */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal currentAmount;

    /** Cuotas vencidas arrastradas de ciclos anteriores (capital más interés compensatorio). */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal overdueAmount;

    /** Mora proyectada a la fecha de pago; se cobra aparte del capital. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal lateInterest;

    /** Lo que se paga si se paga en la fecha de pago: current + overdue + mora. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalToPay;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("purchasedAt ASC, installmentNumber ASC")
    private List<StatementLine> lines = new ArrayList<>();

    /** Una cuota a incluir, con su mora ya calculada por el motor. */
    public record LineDraft(PurchaseInstallment installment, int lateDays, BigDecimal lateInterest) {
    }

    /**
     * Emite un estado de cuenta. Es la única forma de crearlo, y verifica que
     * todas las cuotas estén pendientes, venzan a más tardar en la fecha de
     * pago y estén en la moneda del estado.
     */
    public static Statement register(Long storeId,
                                     Long clientId,
                                     LocalDate cycleCutoffDate,
                                     LocalDate paymentDate,
                                     LocalDateTime issuedAt,
                                     Currency currency,
                                     List<LineDraft> drafts) {
        if (storeId == null || clientId == null || cycleCutoffDate == null || paymentDate == null
                || issuedAt == null || currency == null) {
            throw new IllegalArgumentException("Faltan datos obligatorios del estado de cuenta");
        }
        if (!paymentDate.isAfter(cycleCutoffDate)) {
            throw new IllegalArgumentException("La fecha de pago debe ser posterior al corte");
        }
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("El estado de cuenta debe incluir al menos una cuota");
        }

        Statement statement = new Statement();
        statement.storeId = storeId;
        statement.clientId = clientId;
        statement.cycleCutoffDate = cycleCutoffDate;
        statement.paymentDate = paymentDate;
        statement.issuedAt = issuedAt;
        statement.currency = currency;
        statement.status = StatementStatus.ISSUED;

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        BigDecimal late = BigDecimal.ZERO;
        for (LineDraft draft : drafts) {
            PurchaseInstallment installment = draft.installment();
            if (installment == null || !installment.isPending()) {
                throw new IllegalArgumentException("Solo se pueden incluir cuotas pendientes de pago");
            }
            if (installment.getDueDate().isAfter(paymentDate)) {
                throw new IllegalArgumentException("La cuota " + installment.getInstallmentNumber()
                        + " vence después de la fecha de pago del ciclo");
            }
            if (installment.getPurchase().getCurrency() != currency) {
                throw new IllegalArgumentException("Todas las cuotas deben estar en la moneda del estado de cuenta");
            }
            if (draft.lateDays() < 0 || draft.lateInterest() == null || draft.lateInterest().signum() < 0) {
                throw new IllegalArgumentException("La mora de la cuota no es válida");
            }
            boolean isOverdue = installment.getDueDate().isBefore(paymentDate);
            BigDecimal lateInterest = draft.lateInterest().setScale(2, RoundingMode.HALF_UP);
            statement.lines.add(StatementLine.create(
                    statement, installment, isOverdue, draft.lateDays(), lateInterest));
            if (isOverdue) {
                overdue = overdue.add(installment.getAmount());
            } else {
                current = current.add(installment.getAmount());
            }
            late = late.add(lateInterest);
        }

        BigDecimal total = current.add(overdue).add(late);
        if (total.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El total del estado de cuenta excede el máximo permitido");
        }
        statement.currentAmount = current;
        statement.overdueAmount = overdue;
        statement.lateInterest = late;
        statement.totalToPay = total;
        return statement;
    }

    /** Sus cuotas se arrastraron a un estado más reciente; queda solo como historial. */
    public void supersede() {
        if (status != StatementStatus.ISSUED) {
            throw new IllegalStateException("Solo un estado de cuenta emitido puede reemplazarse");
        }
        this.status = StatementStatus.SUPERSEDED;
    }

    /** Vista de solo lectura: las líneas solo cambian al emitir el estado. */
    public List<StatementLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}