package com.crediflow.store_credit_management.clients.dto;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.domain.model.ClientStatus;
import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;
import com.crediflow.store_credit_management.clients.domain.model.DocumentType;
import com.crediflow.store_credit_management.credit.engine.BillingCalendar;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.CapitalizationPeriod;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.InterestRate;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.RatePeriod;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.RateType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Client DTOs
 * @summary
 * Objetos de entrada (Request) y de salida (Resource) del módulo clients.
 * Los controladores NUNCA exponen entidades JPA: reciben Requests y devuelven
 * Resources. Los Requests validan el formato (campos vacíos, rangos); las
 * reglas de negocio (límite, tienda, deuda) las valida el servicio.
 *
 * @since 1.0.0
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

    public record CreateClientRequest(
            @NotNull DocumentType documentType,
            @NotBlank @Size(min = 8, max = 20) String documentNumber,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String lastName,
            @Size(max = 20) String phone,
            @Email @Size(max = 100) String email,
            @Size(max = 200) String address
    ) {
    }

    /** El documento y la tienda no se pueden cambiar, por eso no aparecen aquí. */
    public record UpdateClientRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String lastName,
            @Size(max = 20) String phone,
            @Email @Size(max = 100) String email,
            @Size(max = 200) String address
    ) {
    }

    /** Tasa tal como la pacta el administrador. El sistema la convierte a TEA. */
    public record InterestRateRequest(
            @NotNull RateType type,

            @Schema(description = "Tasa como fracción decimal: 0.24 equivale a 24 %", example = "0.24")
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 9)
            BigDecimal value,

            @Schema(description = "Período al que se refiere la tasa", example = "ANNUAL")
            @NotNull RatePeriod period,

            @Schema(description = "Solo para tasas NOMINAL; debe omitirse en las EFFECTIVE", example = "MONTHLY")
            CapitalizationPeriod capitalization
    ) {
        /** Convierte la tasa a TEA una sola vez (decisión #3). */
        public InterestRate toValueObject() {
            if (type == RateType.NOMINAL) {
                if (capitalization == null) {
                    throw new IllegalArgumentException("Una tasa nominal requiere un período de capitalización");
                }
                return InterestRate.fromNominal(value, period, capitalization);
            }
            if (capitalization != null) {
                throw new IllegalArgumentException("Una tasa efectiva no lleva período de capitalización");
            }
            return InterestRate.fromEffective(value, period);
        }
    }

    /**
     * Crea la condición de crédito de un cliente. Si ya tenía una vigente, el
     * servicio la cierra y esta pasa a ser la nueva (se conserva el historial).
     */
    public record CreateCreditConditionRequest(
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
            BigDecimal creditLimit,

            @NotNull Currency currency,
            @NotNull @Valid InterestRateRequest compensatoryRate,
            @NotNull @Valid InterestRateRequest lateRate,

            @NotNull @Min(1) @Max(60) Integer maxInstallmentMonths,

            @NotNull @Min(BillingCalendar.MIN_DAY) @Max(BillingCalendar.MAX_DAY) Integer cutoffDay,
            @NotNull LocalTime cutoffTime,
            @NotNull @Min(BillingCalendar.MIN_DAY) @Max(BillingCalendar.MAX_DAY) Integer paymentDay
    ) {
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record ClientResource(
            Long id,
            DocumentType documentType,
            String documentNumber,
            String name,
            String lastName,
            String phone,
            String email,
            String address,
            ClientStatus status
    ) {
        public static ClientResource from(Client client) {
            return new ClientResource(
                    client.getId(),
                    client.getDocumentType(),
                    client.getDocumentNumber(),
                    client.getName(),
                    client.getLastName(),
                    client.getPhone(),
                    client.getEmail(),
                    client.getAddress(),
                    client.getStatus());
        }
    }

    public record InterestRateResource(
            RateType type,
            BigDecimal value,
            RatePeriod period,
            CapitalizationPeriod capitalization,
            @Schema(description = "TEA con la que realmente calcula el sistema")
            BigDecimal effectiveAnnualRate
    ) {
        public static InterestRateResource from(InterestRate rate) {
            return new InterestRateResource(
                    rate.originalType(),
                    rate.originalValue(),
                    rate.originalPeriod(),
                    rate.originalCapitalization(),
                    rate.effectiveAnnualRate());
        }
    }

    public record CreditConditionResource(
            Long id,
            Long clientId,
            BigDecimal creditLimit,
            Currency currency,
            InterestRateResource compensatoryRate,
            InterestRateResource lateRate,
            int maxInstallmentMonths,
            int cutoffDay,
            LocalTime cutoffTime,
            int paymentDay,
            boolean active,
            LocalDate validFrom,
            LocalDate validTo
    ) {
        public static CreditConditionResource from(CreditCondition condition) {
            return new CreditConditionResource(
                    condition.getId(),
                    condition.getClient().getId(),
                    condition.getCreditLimit().amount(),
                    condition.currency(),
                    InterestRateResource.from(condition.getCompensatoryRate()),
                    InterestRateResource.from(condition.getLateRate()),
                    condition.getMaxInstallmentMonths(),
                    condition.getCutoffDay(),
                    condition.getCutoffTime(),
                    condition.getPaymentDay(),
                    condition.isActive(),
                    condition.getValidFrom(),
                    condition.getValidTo());
        }
    }
}