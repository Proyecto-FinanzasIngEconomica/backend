package com.crediflow.store_credit_management.credit.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * InstallmentPlan
 * @summary Resultado completo de financiar una compra en cuotas.
 *
 * @param purchaseAmount       monto original de la compra
 * @param graceDays            días de gracia (compra → fecha de pago del ciclo)
 * @param capitalizedPrincipal K: monto + interés de gracia capitalizado
 * @param graceInterest        interés generado durante la gracia
 * @param monthlyRate          tasa efectiva de 30 días usada en la cuota
 * @param installmentAmount    cuota constante calculada (la última puede variar
 *                             unos céntimos porque absorbe el redondeo)
 * @param installments         cronograma completo
 */
public record InstallmentPlan(
    BigDecimal purchaseAmount,
        int graceDays,
        BigDecimal capitalizedPrincipal,
        BigDecimal graceInterest,
        BigDecimal monthlyRate,
        BigDecimal installmentAmount,
        List<Installment> installments
) {
    public InstallmentPlan {
        installments = List.copyOf(installments);
    }
 
    /** Suma de los intereses de todas las cuotas (sin el interés de gracia). */
    public BigDecimal totalInterest() {
        return installments.stream()
                .map(Installment::interest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
 
    /** Suma de todas las cuotas. */
    public BigDecimal totalPayments() {
        return installments.stream()
                .map(Installment::payment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
