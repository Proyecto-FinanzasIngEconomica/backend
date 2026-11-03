package com.crediflow.store_credit_management.stores.domain.model;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Store
 * @summary
 * Establecimiento que opera en la plataforma. Es el "inquilino" del sistema:
 * cada cliente, producto, compra y pago pertenece a una tienda. El alta y la
 * baja las realiza el administrador del sistema.
 *
 * El RUC identifica a la tienda y no se cambia después del alta.
 *
 * @since 1.0.0
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends AuditableModel {

    private static final Pattern RUC_FORMAT = Pattern.compile("\\d{11}");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_type_id", nullable = false)
    private BusinessType businessType;

    /** Nombre comercial. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Razón social del titular. */
    @Column(length = 150)
    private String legalName;

    @Column(nullable = false, unique = true, updatable = false, length = 11)
    private String ruc;

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StoreStatus status;

    @Column
    private LocalDateTime deactivatedAt;

    /** Da de alta una tienda. Es la única forma de crearla. */
    public static Store register(BusinessType businessType,
                                 String name,
                                 String legalName,
                                 String ruc,
                                 String address,
                                 String phone,
                                 String email) {
        if (ruc == null || !RUC_FORMAT.matcher(ruc.trim()).matches()) {
            throw new IllegalArgumentException("El RUC debe tener exactamente 11 dígitos");
        }
        Store store = new Store();
        store.ruc = ruc.trim();
        store.status = StoreStatus.ACTIVE;
        store.updateDetails(businessType, name, legalName, address, phone, email);
        return store;
    }

    /** Actualiza los datos editables (el RUC no cambia nunca). */
    public void updateDetails(BusinessType businessType,
                              String name,
                              String legalName,
                              String address,
                              String phone,
                              String email) {
        if (businessType == null) {
            throw new IllegalArgumentException("El giro de negocio es obligatorio");
        }
        this.businessType = businessType;
        this.name = requireText(name, 100, "El nombre");
        this.legalName = optionalText(legalName, 150, "La razón social");
        this.address = optionalText(address, 200, "La dirección");
        this.phone = optionalText(phone, 20, "El teléfono");
        this.email = optionalText(email, 100, "El correo");
    }

    /** Baja lógica: la tienda deja de operar pero no se borra. */
    public void deactivate() {
        this.status = StoreStatus.INACTIVE;
        this.deactivatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == StoreStatus.ACTIVE;
    }

    private static String requireText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " es obligatorio");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " no puede superar " + max + " caracteres");
        }
        return trimmed;
    }

    private static String optionalText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " no puede superar " + max + " caracteres");
        }
        return trimmed;
    }
}
