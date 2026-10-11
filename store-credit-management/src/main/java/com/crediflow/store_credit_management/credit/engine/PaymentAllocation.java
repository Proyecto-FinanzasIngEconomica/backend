package com.crediflow.store_credit_management.credit.engine;

import java.math.BigDecimal;

/**
 * PaymentAllocation
 * @summary
 * Imputación de un pago total (decisión #14): primero el interés moratorio,
 * luego el interés compensatorio y por último el capital.
 * Como no hay pagos parciales, el pago siempre cubre las tres partes completas.
 */
public record PaymentAllocation(
    BigDecimal lateInterest,
        BigDecimal compensatoryInterest,
        BigDecimal principal
) {
    public PaymentAllocation {
        lateInterest = RateMath.roundMoney(lateInterest);
        compensatoryInterest = RateMath.roundMoney(compensatoryInterest);
        principal = RateMath.roundMoney(principal);
        if (lateInterest.signum() < 0 || compensatoryInterest.signum() < 0 || principal.signum() < 0) {
            throw new IllegalArgumentException("Ninguna parte del pago puede ser negativa");
        }
    }
 
    /** Total que el cliente debe pagar. */
    public BigDecimal total() {
        return lateInterest.add(compensatoryInterest).add(principal);
    }
}
