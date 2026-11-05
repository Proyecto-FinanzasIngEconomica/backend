package com.crediflow.store_credit_management.stores.dto;

import com.crediflow.store_credit_management.stores.domain.model.BusinessType;
import com.crediflow.store_credit_management.stores.domain.model.Store;
import com.crediflow.store_credit_management.stores.domain.model.StoreStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * Store DTOs
 * @summary
 * Objetos de entrada (Request) y salida (Resource) del módulo stores. Los
 * Requests validan el formato; las reglas de negocio (RUC repetido, giro
 * inexistente) las valida el servicio.
 *
 * @since 1.0.0
 */
public final class StoreDtos {

    private StoreDtos() {
    }

    // ------------------------------------------------------------------
    // Entrada
    // ------------------------------------------------------------------

        public record CreateStoreRequest(
            @Schema(description = "Id del giro de negocio (ver GET /api/v1/business-types)", example = "1")
            @NotNull Long businessTypeId,

            @NotBlank @Size(max = 100) String name,
            @Size(max = 150) String legalName,

            @Schema(example = "20123456789")
            @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener exactamente 11 dígitos")
            String ruc,

            @Size(max = 200) String address,
            @Size(max = 20) String phone,
            @Email @Size(max = 100) String email,

            @Schema(description = "Cuenta del administrador de la tienda; se crea junto con ella")
            @NotNull @Valid StoreAdminRequest admin
    ) {
    }

    public record StoreAdminRequest(
            @Schema(example = "bodegaperez")
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4,50}",
                    message = "debe tener entre 4 y 50 letras o números")
            String username,

            @NotBlank @Size(min = 8, max = 100) String password
    ) {
        @Override
        public String toString() {
            return "StoreAdminRequest[username=" + username + ", password=***]";
        }
    } {
    }

    /** El RUC no se puede cambiar, por eso no aparece aquí. */
    public record UpdateStoreRequest(
            @NotNull Long businessTypeId,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 150) String legalName,
            @Size(max = 200) String address,
            @Size(max = 20) String phone,
            @Email @Size(max = 100) String email
    ) {
    }

    // ------------------------------------------------------------------
    // Salida
    // ------------------------------------------------------------------

    public record BusinessTypeResource(Long id, String name) {
        public static BusinessTypeResource from(BusinessType type) {
            return new BusinessTypeResource(type.getId(), type.getName());
        }
    }

    public record StoreResource(
            Long id,
            BusinessTypeResource businessType,
            String name,
            String legalName,
            String ruc,
            String address,
            String phone,
            String email,
            StoreStatus status
    ) {
        public static StoreResource from(Store store) {
            return new StoreResource(
                    store.getId(),
                    BusinessTypeResource.from(store.getBusinessType()),
                    store.getName(),
                    store.getLegalName(),
                    store.getRuc(),
                    store.getAddress(),
                    store.getPhone(),
                    store.getEmail(),
                    store.getStatus());
        }
    }
}
