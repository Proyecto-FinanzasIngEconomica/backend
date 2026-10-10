package com.crediflow.store_credit_management.clients.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Currency;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditCondition extends AuditableModel{

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    //Limite de credito
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "credit_limit_amount", precision = 12, scale = 2, nullable = false)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "currency", length = 3, nullable = false))
    })
    private Money creditLimit;

    //Tasa compesatoria: el costo del financiamiento
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "originalType",
                    column = @Column(name = "rate_type", length = 10, nullable = false)),
            @AttributeOverride(name = "originalValue",
                    column = @Column(name = "rate_value", precision = 12, scale = 9, nullable = false)),
            @AttributeOverride(name = "originalPeriod",
                    column = @Column(name = "rate_period", length = 10, nullable = false)),
            @AttributeOverride(name = "originalCapitalization",
                    column = @Column(name = "rate_capitalization", length = 10)),
            @AttributeOverride(name = "effectiveAnnualRate",
                    column = @Column(name = "tea", precision = 12, scale = 9, nullable = false))
    })
    private InterestRate compensatoryRate;

    //Maximo de meses en que se puede financiar una compra
    @Column(nullable = false)
    private int maxInstallmentMonths;

    @Column(nullable = false)
    private int cutoffDay;

    @Column(nullable = false)
    private LocalTime cutoffTime;

    @Column(nullable = false)
    private int paymentDay;

    //la condicion en uso del cliente
    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column
    private LocalDate validTo;

    //Crea una nueva condicion validando reglas
    public static CreditCondition create(Client client,
                                         Money creditLimit,
                                         InterestRate compensatoryRate,
                                         InterestRate lateRate,
                                         int maxInstallmentMonths,
                                         int cutoffDay,
                                         LocalTime cutoffTime,
                                         int paymentDay,
                                         LocalDate validFrom) {
        if (client == null || creditLimit == null || compensatoryRate == null
                || lateRate == null || cutoffTime == null || validFrom == null) {
            throw new IllegalArgumentException("Faltan datos obligatorios de la condición de crédito");
        }
        if (creditLimit.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El límite de crédito debe ser mayor a cero");
        }
        if (maxInstallmentMonths < 1) {
            throw new IllegalArgumentException("El plazo máximo debe ser de al menos 1 mes");
        }
        requireDay(cutoffDay, "día de corte");
        requireDay(paymentDay, "día de pago");

        CreditCondition condition = new CreditCondition();
        condition.client = client;
        condition.creditLimit = creditLimit;
        condition.compensatoryRate = compensatoryRate;
        condition.lateRate = lateRate;
        condition.maxInstallmentMonths = maxInstallmentMonths;
        condition.cutoffDay = cutoffDay;
        condition.cutoffTime = cutoffTime;
        condition.paymentDay = paymentDay;
        condition.validFrom = validFrom;
        condition.active = true;
        return condition;
    }

    //Cierra la condición y se conserva en el historial
    public void close(LocalDate closingDate) {
        if (!this.active) {
            throw new IllegalStateException("La condición ya está cerrada");
        }
        this.active = false;
        this.validTo = closingDate;
    }

    //Moneda de todo el crédito
    public Currency currency() {
        return creditLimit.currency();
    }

    //TEA compensatoria
    public BigDecimal compensatoryTea() {
        return compensatoryRate.effectiveAnnualRate();
    }

    //TEA moratoria
    public BigDecimal lateTea() {
        return lateRate.effectiveAnnualRate();
    }

    private static void requireDay(int day, String field) {
        if (day < BillingCalendar.MIN_DAY || day > BillingCalendar.MAX_DAY) {
            throw new IllegalArgumentException("El " + field + " debe estar entre "
                    + BillingCalendar.MIN_DAY + " y " + BillingCalendar.MAX_DAY);
        }
    }
}
