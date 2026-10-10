package com.crediflow.store_credit_management.shared.interfaces.rest.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.crediflow.store_credit_management.shared.interfaces.rest.resources.MessageResource;

@RestControllerAdvice 
public class GlobalExceptionHandler {
    
    private static final int UNPROCESSABLE = 420;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<MessageResource> handleNotFound(ResourceNotFoundException ex) {
        MessageResource messageResource = new MessageResource(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new MessageResource(ex.getMessage()));
    }

    @ExceptionHandler(BussinessRuleException.class)
    public ResponseEntity<MessageResource> handleBusinessRuleViolation(BussinessRuleException ex) {
        return ResponseEntity.status(UNPROCESSABLE)
                .body(new MessageResource(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResource> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MessageResource(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResource> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MessageResource(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MessageResource> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MessageResource("El cuerpo de la solicitud está mal formado o tiene valores inválidos"));
    }
}
