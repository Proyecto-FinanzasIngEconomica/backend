package com.crediflow.store_credit_management.clients.service;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.domain.model.ClientStatus;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.CreateClientRequest;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.UpdateClientRequest;
import com.crediflow.store_credit_management.clients.repository.ClientRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client Service
 * @summary
 * Casos de uso sobre clientes: alta, consulta, edición y baja lógica.
 *
 * Todos los métodos reciben el storeId de la tienda que opera. Ese valor debe
 * venir de la sesión (el token del administrador), nunca de lo que escriba el
 * usuario, y se usa en CADA consulta para que una tienda no vea a otra.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientBalancePort balancePort;

    /** Da de alta un cliente en la tienda. El documento no puede repetirse en ella. */
    @Transactional
    public Client register(Long storeId, CreateClientRequest request) {
        String documentNumber = request.documentNumber().trim().toUpperCase();
        if (clientRepository.existsByStoreIdAndDocumentNumber(storeId, documentNumber)) {
            throw new BussinessRuleException(
                    "Ya existe un cliente con el documento " + documentNumber + " en esta tienda");
        }
        Client client = Client.register(
                storeId,
                request.documentType(),
                documentNumber,
                request.name(),
                request.lastName(),
                request.phone(),
                request.email(),
                request.address());
        return clientRepository.save(client);
    }

    /** Busca un cliente de la tienda; si no existe o es de otra, responde 404. */
    @Transactional(readOnly = true)
    public Client get(Long storeId, Long clientId) {
        return clientRepository.findByIdAndStoreId(clientId, storeId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Cliente", clientId.toString()));
    }

    /** Lista paginada de clientes; si status es null, trae todos. */
    @Transactional(readOnly = true)
    public Page<Client> list(Long storeId, ClientStatus status, Pageable pageable) {
        return status == null
                ? clientRepository.findByStoreId(storeId, pageable)
                : clientRepository.findByStoreIdAndStatus(storeId, status, pageable);
    }

    /** Actualiza los datos generales. Los cambios se guardan al cerrar la transacción. */
    @Transactional
    public Client update(Long storeId, Long clientId, UpdateClientRequest request) {
        Client client = get(storeId, clientId);
        client.updateContactData(
                request.name(),
                request.lastName(),
                request.phone(),
                request.email(),
                request.address());
        return client;
    }

    /**
     * Baja lógica. Se rechaza si el cliente ya está de baja o si tiene saldo
     * pendiente: el historial de lo que debe se conserva siempre.
     */
    @Transactional
    public Client deactivate(Long storeId, Long clientId) {
        Client client = get(storeId, clientId);
        if (!client.isActive()) {
            throw new BussinessRuleException("El cliente ya está dado de baja");
        }
        if (balancePort.hasOutstandingBalance(clientId)) {
            throw new BussinessRuleException(
                    "No se puede dar de baja al cliente: tiene saldo pendiente de pago");
        }
        client.deactivate();
        return client;
    }
}