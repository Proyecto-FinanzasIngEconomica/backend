package com.crediflow.store_credit_management.creditAndBilling.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/*
    Value Object: Money
    representación {2 decimales, redondeo HALF_UP} y una moneda explícita.

    @param amount importe, siempre con 2 decimales
    @currency moneda del importe (PEN o USD)
 */
@Embeddable 
public record Money(
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    BigDecimal amount,

    @Enumerated(EnumType.STRING)
    @Column (name = "currency", nullable = false, length = 3)
    Currency currency
) {
    private static final int PRESENTATION_SCALE = 2;

    public Money{
        Objects.requireNonNull(amount, "El importe no puede ser nulo");
        Objects.requireNonNull(currency, "La moneda no puede ser nula");
        amount = amount.setScale(PRESENTATION_SCALE, RoundingMode.HALF_UP);
    }

    // Crear un Money en cero para la moneda indicada puede servir para sumar intereses de un listado.
    public static Money zero(Currency currency){
        return new Money(BigDecimal.ZERO, currency);
    }

    //Sumar dos importes de la misma moneda
    public Money add(Money other){
        requiresSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    //Restar dos importes de la misma moneda
    public Money subtract(Money other){
        requiresSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    //Multiplica el importe por un factor como una tasa o un factor de capitalización
    //Este factor no es una moneda, es un número decimal que puede ser mayor o menor que 1.
    public Money multiply(BigDecimal factor){
        Objects.requireNonNull(factor, "El factor no puede ser nulo");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isZero(){
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative(){
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThan(Money other){
        requiresSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public void requiresSameCurrency(Money other){
        Objects.requireNonNull(other, "El importe a comparar no puede ser nulo");
        if(this.currency != other.currency){
            throw new IllegalArgumentException("No se pueden operar importes de distintas monedas: "
                                                + this.currency + " y " + other.currency);
        }
    }

}
