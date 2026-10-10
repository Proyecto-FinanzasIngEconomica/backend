package com.crediflow.store_credit_management.shared.interfaces.rest.exception;


//Se lanza cuando se viola una regla de negocio 
public class BussinessRuleException extends RuntimeException {

    public BussinessRuleException(String message) {
        super(message);
    }
}
