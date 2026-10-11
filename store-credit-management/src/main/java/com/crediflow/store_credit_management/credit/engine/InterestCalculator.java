package com.crediflow.store_credit_management.credit.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * InterestCalculator
 * @summary
 * Interés compensatorio por días (compras a fin de mes, fórmula F8) e
 * interés moratorio (fórmula F9). Ambos son interés SIMPLE respecto de la
 * base: se calcula una sola vez sobre el capital o el total exigible, sin
 * capitalizar día a día (decisión #6). La tasa del período sí sale de la
 * TEA con la convención 30/360.
 */
public final class InterestCalculator {
    private InterestCalculator() {
    }
 
    /** Días calendario entre dos fechas (del 15 al 26 de setiembre = 11). */
    public static int daysBetween(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0) {
            throw new IllegalArgumentException("La fecha final es anterior a la inicial");
        }
        return (int) days;
    }
 
    /**
     * Interés de {@code base} durante {@code days} días a una TEA dada,
     * redondeado a 2 decimales.
     */
    public static BigDecimal interestForDays(BigDecimal base, BigDecimal tea, int days) {
        if (base == null || base.signum() < 0) {
            throw new IllegalArgumentException("La base de cálculo no puede ser nula ni negativa");
        }
        return RateMath.roundMoney(base.multiply(RateMath.rateForDays(tea, days)));
    }
 
    /**
     * Interés compensatorio de una compra a fin de mes:
     * desde la fecha de compra hasta la fecha de pago pactada.
     */
    public static BigDecimal endOfMonthInterest(BigDecimal capital,
                                                BigDecimal tea,
                                                LocalDate purchaseDate,
                                                LocalDate paymentDate) {
        return interestForDays(capital, tea, daysBetween(purchaseDate, paymentDate));
    }
 
    /** Días de mora: 0 si se paga hasta la fecha pactada, incluida esta. */
    public static int lateDays(LocalDate dueDate, LocalDate paidDate) {
        long days = ChronoUnit.DAYS.between(dueDate, paidDate);
        return (int) Math.max(0, days);
    }
 
    /**
     * Interés moratorio sobre el total que debió pagarse (capital más interés
     * compensatorio), usando la TEA moratoria pactada.
     */
    public static BigDecimal lateInterest(BigDecimal totalDue, BigDecimal lateTea, int lateDays) {
        if (lateDays == 0) {
            return RateMath.roundMoney(BigDecimal.ZERO);
        }
        return interestForDays(totalDue, lateTea, lateDays);
    }
}
