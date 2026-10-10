package com.crediflow.store_credit_management.shared.domain.model.valueobjetcs;

import lombok.Getter;

@Getter 
public enum CapitalizationPeriod {
    DAILY(1),
    MONTHLY(30);

    private final int days;

    CapitalizationPeriod(int days) {
        this.days = days;
    }
}
