package com.crediflow.store_credit_management.credit.dto;

import com.crediflow.store_credit_management.credit.domain.model.Payment;
import com.crediflow.store_credit_management.credit.domain.model.PaymentLine;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment DTOs
 * @summary
 * Objetos de entrada (Request) y salida (Resource) del módulo credit para
 * pagos. Los Requests validan el formato; las reglas de negocio (estado
 * pagable, monto exacto, fecha válida) las valida el servicio.
 *
 * @since 1.0.0
 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    /**
     * Paga un estado de cuenta completo. El monto debe coincidir EXACTAMENTE
     * con el total calculado a la fecha del pago; no se admiten pagos parciales.
     */
    public record CreatePaymentRequest(
            @Schema(description = "Monto recibido. Debe ser igual al total a pagar a la fecha del pago "
                    + "(capital + interés compensatorio + mora real).", example = "315.18")
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
            BigDecimal amount,

            @Schema(description = "Fecha y hora del pago (ISO-8601). Si se omite, se usa la hora actual. "
                    + "No admite fechas futuras ni anteriores a la emisión del estado de cuenta.",
                    example = "2026-10-28T10:00:00")
            @PastOrPresent LocalDateTime paidAt
    ) {
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record PaymentLineResource(
            Long id,
            Long purchaseId,
            Long installmentId,
            int installmentNumber,
            LocalDate dueDate,
            @Schema(description = "Días entre el vencimiento y el pago real (0 si se pagó a tiempo)")
            int lateDays,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal lateInterestPaid,
            BigDecimal total
    ) {
        public static PaymentLineResource from(PaymentLine line) {
            return new PaymentLineResource(
                    line.getId(),
                    line.getPurchaseId(),
                    line.getInstallmentId(),
                    line.getInstallmentNumber(),
                    line.getDueDate(),
                    line.getLateDays(),
                    line.getPrincipalPaid(),
                    line.getInterestPaid(),
                    line.getLateInterestPaid(),
                    line.getTotal());
        }
    }

    /** Detalle completo: desglose del pago y de cada cuota. */
    public record PaymentResource(
            Long id,
            Long clientId,
            Long statementId,
            LocalDateTime paidAt,
            Currency currency,
            @Schema(description = "Capital pagado: es lo que libera la línea de crédito")
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            @Schema(description = "Mora con los días reales de atraso, cobrada aparte del capital")
            BigDecimal lateInterestPaid,
            BigDecimal totalPaid,
            List<PaymentLineResource> lines
    ) {
        public static PaymentResource from(Payment payment) {
            return new PaymentResource(
                    payment.getId(),
                    payment.getClientId(),
                    payment.getStatementId(),
                    payment.getPaidAt(),
                    payment.getCurrency(),
                    payment.getPrincipalPaid(),
                    payment.getInterestPaid(),
                    payment.getLateInterestPaid(),
                    payment.getTotalPaid(),
                    payment.getLines().stream().map(PaymentLineResource::from).toList());
        }
    }

    /** Fila liviana para listados: sin el detalle de cuotas. */
    public record PaymentSummaryResource(
            Long id,
            Long clientId,
            Long statementId,
            LocalDateTime paidAt,
            Currency currency,
            BigDecimal lateInterestPaid,
            BigDecimal totalPaid
    ) {
        public static PaymentSummaryResource from(Payment payment) {
            return new PaymentSummaryResource(
                    payment.getId(),
                    payment.getClientId(),
                    payment.getStatementId(),
                    payment.getPaidAt(),
                    payment.getCurrency(),
                    payment.getLateInterestPaid(),
                    payment.getTotalPaid());
        }
    }
}
