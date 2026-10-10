package com.crediflow.store_credit_management.clients.domain.model;

import java.time.LocalDateTime;

import com.crediflow.store_credit_management.shared.domain.model.entities.AuditableModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity 
@Getter 
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client extends AuditableModel{

    //Tienda dueña del cliente
    @Column(nullable = false, updatable = false)
    private Long storeId;

    //Usuario con el que el cliente consulta su cuenta
    @Column 
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentType documentType;

     //Número de documento del cliente
    @Column(nullable = false, length = 20)
    private String documentNumber;

    //Nombres
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClientStatus status;

    @Column 
    private LocalDateTime desactivatedAt;

    public static Client register(Long storeId,
                                  DocumentType documentType,
                                  String documentNumber,
                                  String name,
                                  String lastName,
                                  String phone,
                                  String email,
                                  String address) {
        if (storeId == null) {
            throw new IllegalArgumentException("La tienda es obligatoria");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio");
        }
        Client client = new Client();
        client.storeId = storeId;
        client.documentType = documentType;
        client.documentNumber = requireText(documentNumber, 8, 20, "El número de documento").toUpperCase();
        client.status = ClientStatus.ACTIVE;
        client.updateContactData(name, lastName, phone, email, address);
        return client;
    }

    //Actualiza los datos generales
    public void updateContactData(String name, String lastName, String phone,
                                  String email, String address) {
        this.name = requireText(name, 1, 100, "El nombre");
        this.lastName = optionalText(lastName, 100, "El apellido");
        this.phone = optionalText(phone, 20, "El teléfono");
        this.email = optionalText(email, 100, "El correo");
        this.address = optionalText(address, 200, "La dirección");
    }

    //Asocia el usuario de acceso
    public void linkUser(Long userId) {
        this.userId = userId;
    }

    //La clase NO sabe si este cliente tiene deuda, el servicio debe encargarse de eso antes de llamar esta funcion
    public void deactivate() {
        this.status = ClientStatus.INACTIVE;
        this.desactivatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == ClientStatus.ACTIVE;
    }

    private static String requireText(String value, int min, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.length() < min || text.length() > max) {
            throw new IllegalArgumentException(
                    field + " debe tener entre " + min + " y " + max + " caracteres");
        }
        return text;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new IllegalArgumentException(field + " no puede superar " + max + " caracteres");
        }
        return text;
    }
}
