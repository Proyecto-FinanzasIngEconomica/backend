package com.crediflow.store_credit_management.shared.domain.model.valueobjetcs;

import lombok.Getter;

@Getter 
public enum RatePeriod {
    MONTHLY(30),
    ANNUAL(360);

    private final int days;

    RatePeriod(int days) {
        this.days = days;
    }
}
