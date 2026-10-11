package com.crediflow.store_credit_management.enginetests;

import org.junit.jupiter.api.Test;

import com.crediflow.store_credit_management.credit.engine.Installment;
import com.crediflow.store_credit_management.credit.engine.InstallmentPlan;
import com.crediflow.store_credit_management.credit.engine.InstallmentScheduler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallmentSchedulerTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** Caso 1 del informe 7.6 (ejemplo del enunciado). */
    private InstallmentPlan caso1() {
        return InstallmentScheduler.generate(
                bd("1200.00"), bd("0.24"),
                LocalDate.of(2026, 9, 15),     // compra
                LocalDate.of(2026, 9, 26),     // pago del ciclo
                26, 3);
    }

    @Test
    void caso1_graceAndCapitalization() {
        InstallmentPlan plan = caso1();
        assertEquals(11, plan.graceDays());                         // CP-02
        assertEquals(bd("1207.91"), plan.capitalizedPrincipal());   // K
        assertEquals(bd("7.91"), plan.graceInterest());
        assertEquals(bd("0.018087582"), plan.monthlyRate());
    }

    @Test
    void caso1_constantPayment() {
        assertEquals(bd("417.29"), caso1().installmentAmount());    // CP-03
    }

    @Test
    void caso1_fullSchedule() {
        List<Installment> rows = caso1().installments();
        assertEquals(3, rows.size());

        assertRow(rows.get(0), 1, LocalDate.of(2026, 10, 26),
                "1207.91", "21.85", "395.44", "417.29", "812.47");
        assertRow(rows.get(1), 2, LocalDate.of(2026, 11, 26),
                "812.47", "14.70", "402.59", "417.29", "409.88");
        assertRow(rows.get(2), 3, LocalDate.of(2026, 12, 26),
                "409.88", "7.41", "409.88", "417.29", "0.00");
    }

    @Test
    void caso1_totals() {
        InstallmentPlan plan = caso1();
        assertEquals(bd("43.96"), plan.totalInterest());
        assertEquals(bd("1251.87"), plan.totalPayments());
    }

    @Test
    void principalsAlwaysSumToCapitalizedAmount() {
        InstallmentPlan plan = InstallmentScheduler.generate(
                bd("987.65"), bd("0.3175"),
                LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 26), 26, 7);
        BigDecimal sum = plan.installments().stream()
                .map(Installment::principal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(plan.capitalizedPrincipal(), sum);
        assertEquals(bd("0.00"), plan.installments().get(6).closingBalance());
    }

    @Test
    void zeroRate_splitsEvenlyAndLastInstallmentAbsorbsRounding() {
        InstallmentPlan plan = InstallmentScheduler.generate(
                bd("100.00"), BigDecimal.ZERO,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 26), 26, 3);
        assertEquals(bd("100.00"), plan.capitalizedPrincipal());
        assertEquals(bd("33.33"), plan.installmentAmount());
        assertEquals(bd("33.34"), plan.installments().get(2).payment());   // absorbe 1 céntimo
        assertEquals(bd("100.00"), plan.totalPayments());
    }

    @Test
    void singleInstallment_repaysEverythingWithOneInterest() {
        InstallmentPlan plan = InstallmentScheduler.generate(
                bd("500.00"), bd("0.24"),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26), 26, 1);
        assertEquals(0, plan.graceDays());
        assertEquals(bd("500.00"), plan.capitalizedPrincipal());
        assertEquals(1, plan.installments().size());
        assertEquals(bd("0.00"), plan.installments().get(0).closingBalance());
    }

    @Test
    void invalidInputs_areRejected() {
        LocalDate d = LocalDate.of(2026, 9, 15);
        LocalDate pay = LocalDate.of(2026, 9, 26);
        assertThrows(IllegalArgumentException.class,
                () -> InstallmentScheduler.generate(bd("0"), bd("0.24"), d, pay, 26, 3));
        assertThrows(IllegalArgumentException.class,
                () -> InstallmentScheduler.generate(bd("100"), bd("0.24"), d, pay, 26, 0));
        // fecha de pago anterior a la compra
        assertThrows(IllegalArgumentException.class,
                () -> InstallmentScheduler.generate(bd("100"), bd("0.24"), pay, d, 26, 3));
    }

    private static void assertRow(Installment row, int number, LocalDate due,
                                  String opening, String interest, String principal,
                                  String payment, String closing) {
        assertEquals(number, row.number());
        assertEquals(due, row.dueDate());
        assertEquals(bd(opening), row.openingBalance());
        assertEquals(bd(interest), row.interest());
        assertEquals(bd(principal), row.principal());
        assertEquals(bd(payment), row.payment());
        assertEquals(bd(closing), row.closingBalance());
    }
}
