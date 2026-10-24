package com.crediflow.store_credit_management.credit.dto;


import com.crediflow.store_credit_management.credit.domain.model.InstallmentStatus;
import com.crediflow.store_credit_management.credit.domain.model.Purchase;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseInstallment;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseItem;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseModality;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Purchase DTOs
 * @summary
 * Objetos de entrada (Request) y salida (Resource) del módulo credit para
 * compras. Los Requests validan el formato; las reglas de negocio (cupo,
 * moneda, modalidad permitida, cuotas máximas) las valida el servicio.
 *
 * @since 1.0.0
 */
public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    /**
     * Registra una compra al crédito. El precio NO se envía: el sistema usa el
     * precio de crédito vigente de cada producto y guarda una copia.
     */
    public record CreatePurchaseRequest(
            @NotNull Long clientId,

            @Schema(description = "Forma de pago", example = "INSTALLMENTS")
            @NotNull PurchaseModality modality,

            @Schema(description = "Obligatorio con INSTALLMENTS (1 a 60, sin superar el máximo de la condición). "
                    + "Debe omitirse con END_OF_MONTH.", example = "3")
            @Min(1) @Max(60) Integer installmentCount,

            @Schema(description = "Fecha y hora de la compra (ISO-8601). Si se omite, se usa la hora actual. "
                    + "No admite fechas futuras.", example = "2026-09-15T10:30:00")
            @PastOrPresent LocalDateTime purchasedAt,

            @NotEmpty @Size(max = 50) List<@Valid PurchaseItemRequest> items
    ) {
    }

    public record PurchaseItemRequest(
            @NotNull Long productId,

            @Schema(description = "Admite hasta 3 decimales (1.5 kg)", example = "2")
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3)
            BigDecimal quantity
    ) {
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record PurchaseItemResource(
            Long id,
            Long productId,
            String productName,
            String unitOfMeasure,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        public static PurchaseItemResource from(PurchaseItem item) {
            return new PurchaseItemResource(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getUnitOfMeasure(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getSubtotal());
        }
    }

    public record InstallmentResource(
            Long id,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal principalPart,
            BigDecimal interestPart,
            InstallmentStatus status,
            LocalDateTime paidAt
    ) {
        public static InstallmentResource from(PurchaseInstallment installment) {
            return new InstallmentResource(
                    installment.getId(),
                    installment.getInstallmentNumber(),
                    installment.getDueDate(),
                    installment.getAmount(),
                    installment.getPrincipalPart(),
                    installment.getInterestPart(),
                    installment.getStatus(),
                    installment.getPaidAt());
        }
    }

    /** Detalle completo: productos y cronograma de cuotas. */
    public record PurchaseResource(
            Long id,
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
            BigDecimal itemsTotal,
            BigDecimal financedAmount,
            BigDecimal totalInterest,
            BigDecimal totalToPay,
            List<PurchaseItemResource> items,
            List<InstallmentResource> installments
    ) {
        public static PurchaseResource from(Purchase purchase) {
            return new PurchaseResource(
                    purchase.getId(),
                    purchase.getClientId(),
                    purchase.getCreditConditionId(),
                    purchase.getPurchasedAt(),
                    purchase.getModality(),
                    purchase.getInstallmentCount(),
                    purchase.getCurrency(),
                    purchase.getTea(),
                    purchase.getLateTea(),
                    purchase.getCycleCutoffDate(),
                    purchase.getCyclePaymentDate(),
                    purchase.getGraceDays(),
                    purchase.getItemsTotal(),
                    purchase.getFinancedAmount(),
                    purchase.getTotalInterest(),
                    purchase.getTotalToPay(),
                    purchase.getItems().stream().map(PurchaseItemResource::from).toList(),
                    purchase.getInstallments().stream().map(InstallmentResource::from).toList());
        }
    }

    /** Fila liviana para listados: sin productos ni cuotas. */
    public record PurchaseSummaryResource(
            Long id,
            Long clientId,
            LocalDateTime purchasedAt,
            PurchaseModality modality,
            int installmentCount,
            Currency currency,
            BigDecimal itemsTotal,
            BigDecimal totalToPay,
            LocalDate cyclePaymentDate
    ) {
        public static PurchaseSummaryResource from(Purchase purchase) {
            return new PurchaseSummaryResource(
                    purchase.getId(),
                    purchase.getClientId(),
                    purchase.getPurchasedAt(),
                    purchase.getModality(),
                    purchase.getInstallmentCount(),
                    purchase.getCurrency(),
                    purchase.getItemsTotal(),
                    purchase.getTotalToPay(),
                    purchase.getCyclePaymentDate());
        }
    }

    /** Estado de la línea de crédito de un cliente: lo que el frontend muestra antes de vender. */
    public record CreditLineResource(
            Currency currency,
            BigDecimal creditLimit,
            BigDecimal usedCredit,
            BigDecimal availableCredit
    ) {
    }
}