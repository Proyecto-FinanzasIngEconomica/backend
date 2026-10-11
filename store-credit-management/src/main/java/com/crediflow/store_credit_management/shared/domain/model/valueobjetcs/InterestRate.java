package com.crediflow.store_credit_management.shared.domain.model.valueobjetcs;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable 
public record InterestRate(
        @Enumerated(EnumType.STRING)
        @Column(length = 10, nullable = false)
        RateType originalType,
 
        @Column(precision = 12, scale = 9, nullable = false)
        BigDecimal originalValue,
 
        @Enumerated(EnumType.STRING)
        @Column(length = 10, nullable = false)
        RatePeriod originalPeriod,
 
        @Enumerated(EnumType.STRING)
        @Column(length = 10)
        CapitalizationPeriod originalCapitalization,
 
        @Column(precision = 12, scale = 9, nullable = false)
        BigDecimal effectiveAnnualRate
){
    public static final int DAYS_IN_YEAR = 360;
    private static final int RATE_SCALE = 9;
    private static final MathContext CALC = MathContext.DECIMAL128;

    public InterestRate {
        Objects.requireNonNull(originalType, "El tipo de tasa no puede ser nulo");
        Objects.requireNonNull(originalValue, "El valor de la tasa no puede ser nulo");
        Objects.requireNonNull(originalPeriod, "El período de la tasa no puede ser nulo");
        Objects.requireNonNull(effectiveAnnualRate, "La TEA no puede ser nula");

    if (originalValue.signum() < 0) {
            throw new IllegalArgumentException("La tasa no puede ser negativa");
        }
        if (originalType == RateType.NOMINAL && originalCapitalization == null) {
            throw new IllegalArgumentException(
                    "Una tasa nominal requiere un período de capitalización");
        }
        if (originalType == RateType.EFFECTIVE && originalCapitalization != null) {
            throw new IllegalArgumentException(
                    "Una tasa efectiva no lleva período de capitalización");
        }

        originalValue = originalValue.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        effectiveAnnualRate = effectiveAnnualRate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    //Crea una tasa efectiva de periodo TEA = (1 + i)^(360 / díasDelPeríodo) - 1
    public static InterestRate fromEffective(BigDecimal rate, RatePeriod period) {
        Objects.requireNonNull(rate, "El valor de la tasa no puede ser nulo");
        Objects.requireNonNull(period, "El período de la tasa no puede ser nulo");
 
        int periodsPerYear = DAYS_IN_YEAR / period.getDays();
        BigDecimal tea = BigDecimal.ONE.add(rate)
                .pow(periodsPerYear, CALC)
                .subtract(BigDecimal.ONE);
 
        return new InterestRate(RateType.EFFECTIVE, rate, period, null, tea);
    }

    //Crea la tasa a partir de una tasa NOMINAL y su período de capitalización
    //TNA = j * (360 / díasDelPeríodo);  m = 360 / díasDeCapitalización
    //TEA = (1 + TNA / m)^m - 1
    public static InterestRate fromNominal(BigDecimal rate,
                                           RatePeriod period,
                                           CapitalizationPeriod capitalization) {
        Objects.requireNonNull(rate, "El valor de la tasa no puede ser nulo");
        Objects.requireNonNull(period, "El período de la tasa no puede ser nulo");
        Objects.requireNonNull(capitalization, "El período de capitalización no puede ser nulo");
 
        int periodsPerYear = DAYS_IN_YEAR / period.getDays();
        BigDecimal annualNominal = rate.multiply(BigDecimal.valueOf(periodsPerYear));
 
        int compoundingsPerYear = DAYS_IN_YEAR / capitalization.getDays();
        BigDecimal periodicRate = annualNominal
                .divide(BigDecimal.valueOf(compoundingsPerYear), CALC);
 
        BigDecimal tea = BigDecimal.ONE.add(periodicRate)
                .pow(compoundingsPerYear, CALC)
                .subtract(BigDecimal.ONE);
 
        return new InterestRate(RateType.NOMINAL, rate, period, capitalization, tea);
    }

    //Una tasa en cero obliga al motor a usar cuota = capital / n (caso 1=0)
    public boolean isZero() {
        return effectiveAnnualRate.signum() == 0;
    }
}
