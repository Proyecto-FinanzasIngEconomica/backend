package com.crediflow.store_credit_management.credit.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RateMath {
    public static final int DAYS_IN_YEAR = 360;
    public static final int DAYS_IN_MONTH = 30;
    public static final int RATE_SCALE = 9;
    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
 
    private RateMath() {
    }
 
    /**
     * Tasa efectiva para {@code days} días, SIN redondear:
     * (1 + TEA)^(days / 360) - 1
     */
    private static BigDecimal rawRateForDays(BigDecimal tea, int days) {
        if (tea == null) {
            throw new IllegalArgumentException("La TEA no puede ser nula");
        }
        if (tea.signum() < 0) {
            throw new IllegalArgumentException("La TEA no puede ser negativa");
        }
        if (days < 0) {
            throw new IllegalArgumentException("Los días no pueden ser negativos");
        }
        double exponent = (double) days / DAYS_IN_YEAR;
        return BigDecimal.valueOf(Math.expm1(Math.log1p(tea.doubleValue()) * exponent));
    }
 
    /**
     * Tasa efectiva equivalente a {@code days} días, redondeada a 9 decimales.
     * Se usa para el interés por días de una compra a fin de mes y para la mora.
     */
    public static BigDecimal rateForDays(BigDecimal tea, int days) {
        return rawRateForDays(tea, days).setScale(RATE_SCALE, ROUNDING);
    }
 
    /**
     * Tasa efectiva mensual comercial (30 días), redondeada a 9 decimales.
     * Es la tasa "i" de la cuota francesa.
     */
    public static BigDecimal monthlyRate(BigDecimal tea) {
        return rateForDays(tea, DAYS_IN_MONTH);
    }
 
    /**
     * Factor de capitalización (1 + TEA)^(days / 360), sin redondear.
     * Se usa para capitalizar el período de gracia (fórmula F5 del informe).
     */
    public static BigDecimal growthFactor(BigDecimal tea, int days) {
        return BigDecimal.ONE.add(rawRateForDays(tea, days));
    }
 
    /** Redondea un importe a 2 decimales (HALF_UP). */
    public static BigDecimal roundMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }
}
