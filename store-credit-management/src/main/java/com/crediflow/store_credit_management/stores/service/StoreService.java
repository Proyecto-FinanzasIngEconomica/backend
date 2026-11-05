package com.crediflow.store_credit_management.stores.service;

import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import com.crediflow.store_credit_management.stores.domain.model.BusinessType;
import com.crediflow.store_credit_management.stores.domain.model.Store;
import com.crediflow.store_credit_management.stores.domain.model.StoreStatus;
import com.crediflow.store_credit_management.stores.dto.StoreDtos.CreateStoreRequest;
import com.crediflow.store_credit_management.stores.dto.StoreDtos.UpdateStoreRequest;
import com.crediflow.store_credit_management.stores.repository.BusinessTypeRepository;
import com.crediflow.store_credit_management.stores.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Store Service
 * @summary
 * Casos de uso sobre tiendas: alta, consulta, listado, edición y baja lógica,
 * más la consulta del catálogo de giros. Solo el administrador del sistema
 * debe llamarlos (lo exigirá IAM).
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final BusinessTypeRepository businessTypeRepository;
    private final StoreBalancePort balancePort;
    private final StoreAdminProvisioner adminProvisioner;

    /** Da de alta una tienda. El RUC no puede repetirse. */
    @Transactional
    public Store register(CreateStoreRequest request) {
        String ruc = request.ruc().trim();
        if (storeRepository.existsByRuc(ruc)) {
            throw new BussinessRuleException("Ya existe una tienda con el RUC " + ruc);
        }
        BusinessType businessType = findBusinessType(request.businessTypeId());
        Store store = Store.register(
                businessType,
                request.name(),
                request.legalName(),
                ruc,
                request.address(),
                request.phone(),
                request.email());
        Store saved = storeRepository.save(store);
        adminProvisioner.createStoreAdmin(
                saved.getId(), request.admin().username(), request.admin().password());
        return saved;
    }

    /** Busca una tienda; si no existe, responde 404. */
    @Transactional(readOnly = true)
    public Store get(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
    }

    /** Lista paginada de tiendas; si status es null, trae todas. */
    @Transactional(readOnly = true)
    public Page<Store> list(StoreStatus status, Pageable pageable) {
        return status == null
                ? storeRepository.findAll(pageable)
                : storeRepository.findByStatus(status, pageable);
    }

    /** Actualiza los datos editables. El RUC no cambia. */
    @Transactional
    public Store update(Long storeId, UpdateStoreRequest request) {
        Store store = get(storeId);
        store.updateDetails(
                findBusinessType(request.businessTypeId()),
                request.name(),
                request.legalName(),
                request.address(),
                request.phone(),
                request.email());
        return store;
    }

    /**
     * Baja lógica. Se rechaza si la tienda ya está de baja o si tiene cuotas
     * pendientes de cobro: sin tienda activa nadie podría registrar esos pagos.
     */
    @Transactional
    public Store deactivate(Long storeId) {
        Store store = get(storeId);
        if (!store.isActive()) {
            throw new BussinessRuleException("La tienda ya está dada de baja");
        }
        if (balancePort.hasOutstandingBalance(storeId)) {
            throw new BussinessRuleException(
                    "No se puede dar de baja la tienda: tiene cuotas pendientes de cobro");
        }
        store.deactivate();
        return store;
    }

    /** Catálogo de giros de negocio, ordenado por nombre. */
    @Transactional(readOnly = true)
    public List<BusinessType> listBusinessTypes() {
        return businessTypeRepository.findAllByOrderByNameAsc();
    }

    private BusinessType findBusinessType(Long businessTypeId) {
        return businessTypeRepository.findById(businessTypeId)
                .orElseThrow();
    }
}