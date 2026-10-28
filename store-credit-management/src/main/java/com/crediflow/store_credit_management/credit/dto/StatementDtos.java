package com.crediflow.store_credit_management.credit.dto;

import com.crediflow.store_credit_management.credit.domain.model.Statement;
import com.crediflow.store_credit_management.credit.domain.model.StatementLine;
import com.crediflow.store_credit_management.credit.domain.model.StatementStatus;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Statement DTOs
 * @summary
 * Objetos de entrada (Request) y salida (Resource) del módulo credit para
 * estados de cuenta.
 *
 * @since 1.0.0
 */
public final class StatementDtos {

    private StatementDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    /**
     * Generación manual del estado de cuenta de un cliente. El cuerpo es
     * opcional: sin él, se usa la hora actual.
     */
    public record GenerateStatementRequest(
            @Schema(description = "Instante de referencia (ISO-8601): se factura el último ciclo cuyo corte ya "
                    + "pasó a esa fecha. Sirve para demostraciones; si se omite, se usa la hora actual.",
                    example = "2026-09-21T08:00:00")
            LocalDateTime asOf
    ) {
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record StatementLineResource(
            Long id,
            Long purchaseId,
            LocalDateTime purchasedAt,
            int installmentNumber,
            int installmentCount,
            LocalDate dueDate,
            BigDecimal principalPart,
            BigDecimal interestPart,
            BigDecimal amount,
            @Schema(description = "true si la cuota venció antes de la fecha de pago del ciclo (viene arrastrada)")
            boolean overdue,
            @Schema(description = "Días de atraso proyectados hasta la fecha de pago del ciclo")
            int lateDays,
            @Schema(description = "Mora proyectada hasta la fecha de pago; el pago real la recalcula con los días reales")
            BigDecimal lateInterest
    ) {
        public static StatementLineResource from(StatementLine line) {
            return new StatementLineResource(
                    line.getId(),
                    line.getPurchaseId(),
                    line.getPurchasedAt(),
                    line.getInstallmentNumber(),
                    line.getInstallmentCount(),
                    line.getDueDate(),
                    line.getPrincipalPart(),
                    line.getInterestPart(),
                    line.getAmount(),
                    line.isOverdue(),
                    line.getLateDays(),
                    line.getLateInterest());
        }
    }

    /** Detalle completo: totales y cuotas ordenadas por fecha de compra. */
    public record StatementResource(
            Long id,
            Long clientId,
            LocalDate cycleCutoffDate,
            LocalDate paymentDate,
            LocalDateTime issuedAt,
            Currency currency,
            StatementStatus status,
            @Schema(description = "Cuotas que vencen en este ciclo")
            BigDecimal currentAmount,
            @Schema(description = "Cuotas vencidas arrastradas de ciclos anteriores")
            BigDecimal overdueAmount,
            @Schema(description = "Mora proyectada a la fecha de pago, cobrada aparte")
            BigDecimal lateInterest,
            @Schema(description = "Lo que se paga en la fecha de pago: currentAmount + overdueAmount + lateInterest")
            BigDecimal totalToPay,
            List<StatementLineResource> lines
    ) {
        public static StatementResource from(Statement statement) {
            return new StatementResource(
                    statement.getId(),
                    statement.getClientId(),
                    statement.getCycleCutoffDate(),
                    statement.getPaymentDate(),
                    statement.getIssuedAt(),
                    statement.getCurrency(),
                    statement.getStatus(),
                    statement.getCurrentAmount(),
                    statement.getOverdueAmount(),
                    statement.getLateInterest(),
                    statement.getTotalToPay(),
                    statement.getLines().stream().map(StatementLineResource::from).toList());
        }
    }

    /** Fila liviana para listados: sin las cuotas. */
    public record StatementSummaryResource(
            Long id,
            Long clientId,
            LocalDate cycleCutoffDate,
            LocalDate paymentDate,
            LocalDateTime issuedAt,
            Currency currency,
            StatementStatus status,
            BigDecimal totalToPay
    ) {
        public static StatementSummaryResource from(Statement statement) {
            return new StatementSummaryResource(
                    statement.getId(),
                    statement.getClientId(),
                    statement.getCycleCutoffDate(),
                    statement.getPaymentDate(),
                    statement.getIssuedAt(),
                    statement.getCurrency(),
                    statement.getStatus(),
                    statement.getTotalToPay());
        }
    }
}
