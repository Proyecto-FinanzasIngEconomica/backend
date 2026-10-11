package com.crediflow.store_credit_management.credit.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

public class BillingCalendar {
    public static final int MIN_DAY = 1;
    public static final int MAX_DAY = 30;
 
    /** Fecha de corte y fecha de pago de un ciclo de facturación. */
    public record BillingCycle(LocalDate cutoffDate, LocalDate paymentDate) {
    }
 
    private BillingCalendar() {
    }
 
    /**
     * Devuelve el día pactado dentro del mes, ajustado al último día
     * del mes si este no tiene tantos días.
     */
    public static LocalDate adjustDay(YearMonth month, int day) {
        requireValidDay(day, "día");
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
 
    /**
     * Determina el ciclo (corte y pago) al que pertenece una compra.
     *
     * @param purchase    fecha y hora de la compra
     * @param cutoffDay   día de corte pactado (1 a 30)
     * @param cutoffTime  hora límite del día de corte
     * @param paymentDay  día de pago pactado (1 a 30)
     */
    public static BillingCycle cycleFor(LocalDateTime purchase,
                                        int cutoffDay,
                                        LocalTime cutoffTime,
                                        int paymentDay) {
        if (purchase == null || cutoffTime == null) {
            throw new IllegalArgumentException("La compra y la hora de corte son obligatorias");
        }
        requireValidDay(cutoffDay, "día de corte");
        requireValidDay(paymentDay, "día de pago");
 
        YearMonth month = YearMonth.from(purchase);
        LocalDate cutoff = adjustDay(month, cutoffDay);
 
        // El instante exacto del corte ya cierra el ciclo (límite exclusivo).
        if (!purchase.isBefore(cutoff.atTime(cutoffTime))) {
            cutoff = adjustDay(month.plusMonths(1), cutoffDay);
        }
 
        YearMonth cutoffMonth = YearMonth.from(cutoff);
        LocalDate payment = adjustDay(cutoffMonth, paymentDay);
        if (!payment.isAfter(cutoff)) {
            payment = adjustDay(cutoffMonth.plusMonths(1), paymentDay);
        }
        return new BillingCycle(cutoff, payment);
    }
 
    /**
     * Fecha de vencimiento de la cuota número {@code installmentNumber}:
     * un mes comercial después de la fecha de pago base, por cada cuota.
     */
    public static LocalDate installmentDueDate(LocalDate firstCyclePaymentDate,
                                               int installmentNumber,
                                               int paymentDay) {
        if (installmentNumber < 1) {
            throw new IllegalArgumentException("El número de cuota empieza en 1");
        }
        YearMonth month = YearMonth.from(firstCyclePaymentDate).plusMonths(installmentNumber);
        return adjustDay(month, paymentDay);
    }
 
    private static void requireValidDay(int day, String name) {
        if (day < MIN_DAY || day > MAX_DAY) {
            throw new IllegalArgumentException(
                    "El " + name + " debe estar entre " + MIN_DAY + " y " + MAX_DAY);
        }
    }
}
