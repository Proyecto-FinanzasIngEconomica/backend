package com.crediflow.store_credit_management.shared.interfaces.rest.exception;

//Se lanza cuando no se encuentra un recurso solicitado
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forId(String resourceName, String id) {
        return new ResourceNotFoundException(resourceName + " with ID " + id + " not found.");
    }
    
}
