package com.crediflow.store_credit_management.enginetests;

import org.junit.jupiter.api.Test;

import com.crediflow.store_credit_management.credit.engine.RateMath;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateMathTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void monthlyRate_tea24_matchesInform() {
        // Informe 7.6, caso 1, paso 5: i_m = 1.24^(30/360) - 1
        assertEquals(bd("0.018087582"), RateMath.monthlyRate(bd("0.24")));
    }

    @Test
    void rateForDays_graceDays_matchesInform() {
        assertEquals(bd("0.006594496"), RateMath.rateForDays(bd("0.24"), 11));
    }

    @Test
    void rateForDays_caso2_compensatory() {
        BigDecimal tea = bd("0.425760887");
        assertEquals(bd("0.020906708"), RateMath.rateForDays(tea, 21));
        assertEquals(bd("0.013889685"), RateMath.rateForDays(tea, 14));
    }

    @Test
    void rateForDays_caso2_late() {
        assertEquals(bd("0.005243126"), RateMath.rateForDays(bd("0.601032219"), 4));
    }

    @Test
    void rateForDays_fullYear_equalsTea() {
        assertEquals(bd("0.240000000"), RateMath.rateForDays(bd("0.24"), 360));
    }

    @Test
    void rateForDays_zeroDays_isZero() {
        assertEquals(0, RateMath.rateForDays(bd("0.24"), 0).signum());
    }

    @Test
    void zeroTea_givesZeroRate() {
        assertEquals(0, RateMath.monthlyRate(BigDecimal.ZERO).signum());
    }

    @Test
    void invalidInputs_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> RateMath.rateForDays(bd("-0.1"), 10));
        assertThrows(IllegalArgumentException.class, () -> RateMath.rateForDays(bd("0.24"), -1));
        assertThrows(IllegalArgumentException.class, () -> RateMath.rateForDays(null, 10));
    }

    @Test
    void roundMoney_isHalfUp() {
        assertEquals(bd("0.13"), RateMath.roundMoney(bd("0.125")));
        assertEquals(bd("0.12"), RateMath.roundMoney(bd("0.124")));
    }
}
