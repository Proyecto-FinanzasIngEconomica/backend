package com.crediflow.store_credit_management.credit.domain.model;


//Estado de una cuenta, solo el más reciente puede estar ISSUED, el anterior sin pagar
//Se establece como SUPERSEDED, las cuotas se arrastran al nuevo
public enum StatementStatus {
    ISSUED,
    PAID,
    SUPERSEDED
}
