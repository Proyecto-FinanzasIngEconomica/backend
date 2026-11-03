package com.crediflow.store_credit_management.stores.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Business Type
 * @summary
 * Giro de negocio de una tienda (bodega, carnicería, peluquería, spa...).
 * Es un catálogo pequeño y estable: se precarga al arrancar y no se edita
 * desde la API.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessType extends AuditableModel {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    public static BusinessType of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del giro es obligatorio");
        }
        if (name.trim().length() > 50) {
            throw new IllegalArgumentException("El nombre del giro no puede superar 50 caracteres");
        }
        BusinessType type = new BusinessType();
        type.name = name.trim();
        return type;
    }
}