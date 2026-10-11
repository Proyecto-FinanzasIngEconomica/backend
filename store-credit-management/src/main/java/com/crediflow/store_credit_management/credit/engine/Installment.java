package com.crediflow.store_credit_management.credit.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Installment(
    int number,
        LocalDate dueDate,
        BigDecimal openingBalance,
        BigDecimal interest,
        BigDecimal principal,
        BigDecimal payment,
        BigDecimal closingBalance
) {

}
