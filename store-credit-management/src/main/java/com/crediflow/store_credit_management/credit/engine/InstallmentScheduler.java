package com.crediflow.store_credit_management.credit.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * InstallmentScheduler
 * @summary
 * Genera el cronograma de una compra en cuotas por el método francés vencido
 * (mes comercial de 30 días), capitalizando antes el período de gracia.
 *
 * Pasos (fórmulas F4 a F7 del informe):
 *  1. g = días entre la compra y la fecha de pago del ciclo.
 *  2. K = monto × (1 + TEA)^(g/360)           → capital capitalizado.
 *  3. i = (1 + TEA)^(30/360) − 1              → tasa mensual.
 *  4. C = K · i · (1+i)^n / ((1+i)^n − 1)     → cuota constante.
 *  5. Por cada cuota: interés = saldo × i; amortización = C − interés.
 *     La última cuota amortiza TODO el saldo restante, de modo que la suma
 *     de amortizaciones coincide exactamente con K (sin descuadre de céntimos).
 */
public final class InstallmentScheduler {
    private static final MathContext CALC = MathContext.DECIMAL128;
 
    private InstallmentScheduler() {
    }
 
    /**
     * @param purchaseAmount          monto de la compra (> 0)
     * @param tea                     TEA compensatoria del cliente
     * @param purchaseDate            fecha de la compra
     * @param firstCyclePaymentDate   fecha de pago del ciclo al que pertenece
     *                                la compra (ver {@link BillingCalendar#cycleFor})
     * @param paymentDay              día de pago pactado (para las demás cuotas)
     * @param numberOfInstallments    número de cuotas (≥ 1)
     */
    public static InstallmentPlan generate(BigDecimal purchaseAmount,
                                           BigDecimal tea,
                                           LocalDate purchaseDate,
                                           LocalDate firstCyclePaymentDate,
                                           int paymentDay,
                                           int numberOfInstallments) {
        if (purchaseAmount == null || purchaseAmount.signum() <= 0) {
            throw new IllegalArgumentException("El monto de la compra debe ser mayor a cero");
        }
        if (numberOfInstallments < 1) {
            throw new IllegalArgumentException("Debe haber al menos una cuota");
        }
        int graceDays = InterestCalculator.daysBetween(purchaseDate, firstCyclePaymentDate);
 
        BigDecimal capitalized = RateMath.roundMoney(
                purchaseAmount.multiply(RateMath.growthFactor(tea, graceDays)));
        BigDecimal graceInterest = capitalized.subtract(RateMath.roundMoney(purchaseAmount));
        BigDecimal monthlyRate = RateMath.monthlyRate(tea);
        BigDecimal installmentAmount = constantPayment(capitalized, monthlyRate, numberOfInstallments);
 
        List<Installment> rows = new ArrayList<>(numberOfInstallments);
        BigDecimal balance = capitalized;
        for (int j = 1; j <= numberOfInstallments; j++) {
            BigDecimal interest = RateMath.roundMoney(balance.multiply(monthlyRate));
            boolean last = (j == numberOfInstallments);
            BigDecimal principal = last ? balance : installmentAmount.subtract(interest);
            BigDecimal payment = principal.add(interest);
            BigDecimal closing = balance.subtract(principal);
 
            rows.add(new Installment(
                    j,
                    BillingCalendar.installmentDueDate(firstCyclePaymentDate, j, paymentDay),
                    balance, interest, principal, payment, closing));
            balance = closing;
        }
 
        return new InstallmentPlan(
                RateMath.roundMoney(purchaseAmount),
                graceDays, capitalized, graceInterest,
                monthlyRate, installmentAmount, rows);
    }
 
    /** Cuota constante del método francés; si i = 0, capital / n. */
    static BigDecimal constantPayment(BigDecimal capital, BigDecimal monthlyRate, int n) {
        if (monthlyRate.signum() == 0) {
            return RateMath.roundMoney(capital.divide(BigDecimal.valueOf(n), CALC));
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(n, CALC);
        BigDecimal numerator = capital.multiply(monthlyRate).multiply(growth);
        BigDecimal denominator = growth.subtract(BigDecimal.ONE);
        return RateMath.roundMoney(numerator.divide(denominator, CALC));
    }
}
