package com.crediflow.store_credit_management.enginetests;

import com.crediflow.store_credit_management.credit.engine.BillingCalendar;
import com.crediflow.store_credit_management.credit.engine.BillingCalendar.BillingCycle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingCalendarTest {

    private static final LocalTime CUTOFF_TIME = LocalTime.of(23, 59);

    @Test
    void purchaseBeforeCutoff_belongsToCurrentCycle() {
        // CP-04: compra 15/09/2026 10:30, corte día 20, pago día 26
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2026, 9, 15, 10, 30), 20, CUTOFF_TIME, 26);
        assertEquals(LocalDate.of(2026, 9, 20), cycle.cutoffDate());
        assertEquals(LocalDate.of(2026, 9, 26), cycle.paymentDate());
    }

    @Test
    void purchaseAfterCutoff_movesToNextCycle() {
        // CP-05: compra 22/09/2026 → corte 20/10, pago 26/10
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2026, 9, 22, 9, 0), 20, CUTOFF_TIME, 26);
        assertEquals(LocalDate.of(2026, 10, 20), cycle.cutoffDate());
        assertEquals(LocalDate.of(2026, 10, 26), cycle.paymentDate());
    }

    @Test
    void purchaseExactlyAtCutoffInstant_movesToNextCycle() {
        // Decisión #5: el instante del corte es límite EXCLUSIVO
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2026, 9, 20, 23, 59, 0), 20, CUTOFF_TIME, 26);
        assertEquals(LocalDate.of(2026, 10, 20), cycle.cutoffDate());
    }

    @Test
    void purchaseOneMinuteBeforeCutoff_staysInCurrentCycle() {
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2026, 9, 20, 23, 58, 0), 20, CUTOFF_TIME, 26);
        assertEquals(LocalDate.of(2026, 9, 20), cycle.cutoffDate());
        assertEquals(LocalDate.of(2026, 9, 26), cycle.paymentDate());
    }

    @Test
    void cutoffDay30InFebruary_usesLastDayOfMonth() {
        // CP-11: febrero de 2027 tiene 28 días
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2027, 2, 10, 10, 0), 30, CUTOFF_TIME, 30);
        assertEquals(LocalDate.of(2027, 2, 28), cycle.cutoffDate());
    }

    @Test
    void paymentDayBeforeCutoffDay_paysInFollowingMonth() {
        // corte 20, pago 10: el pago cae en el mes siguiente al corte
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2026, 9, 5, 10, 0), 20, CUTOFF_TIME, 10);
        assertEquals(LocalDate.of(2026, 9, 20), cycle.cutoffDate());
        assertEquals(LocalDate.of(2026, 10, 10), cycle.paymentDate());
    }

    @Test
    void cutoffInDecember_rollsOverToNextYear() {
        BillingCycle cycle = BillingCalendar.cycleFor(
                LocalDateTime.of(2026, 12, 28, 10, 0), 20, CUTOFF_TIME, 26);
        assertEquals(LocalDate.of(2027, 1, 20), cycle.cutoffDate());
        assertEquals(LocalDate.of(2027, 1, 26), cycle.paymentDate());
    }

    @Test
    void installmentDueDates_followPaymentDay() {
        // Enunciado: 26/10, 26/11 y 26/12 con pago base 26/09
        LocalDate base = LocalDate.of(2026, 9, 26);
        assertEquals(LocalDate.of(2026, 10, 26), BillingCalendar.installmentDueDate(base, 1, 26));
        assertEquals(LocalDate.of(2026, 11, 26), BillingCalendar.installmentDueDate(base, 2, 26));
        assertEquals(LocalDate.of(2026, 12, 26), BillingCalendar.installmentDueDate(base, 3, 26));
    }

    @Test
    void installmentDueDates_shortMonthsUseLastDay() {
        LocalDate base = LocalDate.of(2027, 1, 30);
        assertEquals(LocalDate.of(2027, 2, 28), BillingCalendar.installmentDueDate(base, 1, 30));
        assertEquals(LocalDate.of(2027, 3, 30), BillingCalendar.installmentDueDate(base, 2, 30));
    }

    @Test
    void invalidDays_areRejected() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        assertThrows(IllegalArgumentException.class,
                () -> BillingCalendar.cycleFor(now, 0, CUTOFF_TIME, 26));
        assertThrows(IllegalArgumentException.class,
                () -> BillingCalendar.cycleFor(now, 20, CUTOFF_TIME, 31));
    }
}