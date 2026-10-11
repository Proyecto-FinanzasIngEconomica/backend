package com.crediflow.store_credit_management.enginetests;

import org.junit.jupiter.api.Test;

import com.crediflow.store_credit_management.credit.engine.InterestCalculator;
import com.crediflow.store_credit_management.credit.engine.PaymentAllocation;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Caso 2 del informe 7.6: compras a fin de mes, mora e imputación. */
class InterestCalculatorTest {

    private static final BigDecimal TEA = new BigDecimal("0.425760887");       // 36 % nominal, cap. mensual
    private static final BigDecimal TEA_MORA = new BigDecimal("0.601032219");  // 48 % nominal, cap. mensual
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 9, 26);

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void endOfMonthInterest_firstPurchase() {
        // 05/09 → 26/09 = 21 días
        BigDecimal interest = InterestCalculator.endOfMonthInterest(
                bd("150.00"), TEA, LocalDate.of(2026, 9, 5), PAYMENT_DATE);
        assertEquals(bd("3.14"), interest);
    }

    @Test
    void endOfMonthInterest_secondPurchase() {
        // 12/09 → 26/09 = 14 días
        BigDecimal interest = InterestCalculator.endOfMonthInterest(
                bd("230.50"), TEA, LocalDate.of(2026, 9, 12), PAYMENT_DATE);
        assertEquals(bd("3.20"), interest);
    }

    @Test
    void statementTotal_matchesInform() {
        BigDecimal total = bd("150.00").add(bd("230.50"))
                .add(bd("3.14")).add(bd("3.20"));
        assertEquals(bd("386.84"), total);                                   // CP-04
    }

    @Test
    void lateDays_countsDaysAfterDueDate() {
        assertEquals(4, InterestCalculator.lateDays(PAYMENT_DATE, LocalDate.of(2026, 9, 30)));
    }

    @Test
    void lateDays_isZeroWhenPaidOnOrBeforeDueDate() {
        assertEquals(0, InterestCalculator.lateDays(PAYMENT_DATE, PAYMENT_DATE));
        assertEquals(0, InterestCalculator.lateDays(PAYMENT_DATE, LocalDate.of(2026, 9, 24)));
    }

    @Test
    void lateInterest_matchesInform() {
        // CP-08: 386.84 × 0.005243126 = 2.03
        BigDecimal mora = InterestCalculator.lateInterest(bd("386.84"), TEA_MORA, 4);
        assertEquals(bd("2.03"), mora);
    }

    @Test
    void lateInterest_isZeroWithoutLateDays() {
        assertEquals(bd("0.00"), InterestCalculator.lateInterest(bd("386.84"), TEA_MORA, 0));
    }

    @Test
    void paymentAllocation_followsPriorityAndSumsToTotal() {
        // CP-09: mora 2.03, interés 6.34, capital 380.50 → 388.87
        PaymentAllocation allocation = new PaymentAllocation(bd("2.03"), bd("6.34"), bd("380.50"));
        assertEquals(bd("388.87"), allocation.total());
    }

    @Test
    void paymentAllocation_rejectsNegativeParts() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAllocation(bd("-1"), bd("6.34"), bd("380.50")));
    }

    @Test
    void daysBetween_rejectsReversedDates() {
        assertThrows(IllegalArgumentException.class,
                () -> InterestCalculator.daysBetween(PAYMENT_DATE, LocalDate.of(2026, 9, 5)));
    }
}
