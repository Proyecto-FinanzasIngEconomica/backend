package com.crediflow.store_credit_management.clients.service;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;
import com.crediflow.store_credit_management.clients.dto.ClientDtos.CreateCreditConditionRequest;
import com.crediflow.store_credit_management.clients.repository.CreditConditionRepository;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.InterestRate;
import com.crediflow.store_credit_management.shared.domain.model.valueobjetcs.Money;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Credit Condition Service
 * @summary
 * Casos de uso sobre las condiciones de crédito de un cliente: registrar una
 * nueva (cerrando la vigente), consultar la vigente y ver el historial.
 *
 * También es la puerta por la que el módulo credit leerá las condiciones al
 * registrar una compra: {@link #getActive(Long, Long)}.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class CreditConditionService {

    /** Mayor TEA que cabe en la columna DECIMAL(12,9) de la base de datos. */
    private static final BigDecimal MAX_TEA = new BigDecimal("999.999999999");

    private final ClientService clientService;
    private final CreditConditionRepository conditionRepository;

    /**
     * Registra una condición nueva para el cliente. Si ya tenía una vigente, la
     * cierra en la misma transacción: nunca quedan dos activas, ni queda el
     * cliente sin condición si algo falla a mitad de camino.
     */
    @Transactional
    public CreditCondition register(Long storeId, Long clientId, CreateCreditConditionRequest request) {
        Client client = clientService.get(storeId, clientId);
        if (!client.isActive()) {
            throw new BussinessRuleException(
                    "No se pueden registrar condiciones de crédito a un cliente dado de baja");
        }

        // La conversión a TEA ocurre aquí, una sola vez (decisión #3).
        InterestRate compensatoryRate = request.compensatoryRate().toValueObject();
        InterestRate lateRate = request.lateRate().toValueObject();
        requireTeaWithinLimit(compensatoryRate, "compensatoria");
        requireTeaWithinLimit(lateRate, "moratoria");

        LocalDate today = LocalDate.now();
        conditionRepository.findByClient_IdAndActiveTrue(clientId)
                .ifPresent(current -> current.close(today));

        CreditCondition condition = CreditCondition.create(
                client,
                new Money(request.creditLimit(), request.currency()),
                compensatoryRate,
                lateRate,
                request.maxInstallmentMonths(),
                request.cutoffDay(),
                request.cutoffTime(),
                request.paymentDay(),
                today);
        return conditionRepository.save(condition);
    }

    /** La condición vigente del cliente; 404 si no tiene ninguna. */
    @Transactional(readOnly = true)
    public CreditCondition getActive(Long storeId, Long clientId) {
        clientService.get(storeId, clientId);
        return conditionRepository.findByClient_IdAndActiveTrue(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El cliente " + clientId + " no tiene una condición de crédito vigente"));
    }

    /** Historial de condiciones, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<CreditCondition> history(Long storeId, Long clientId) {
        clientService.get(storeId, clientId);
        return conditionRepository.findByClient_IdOrderByValidFromDescIdDesc(clientId);
    }

    /**
     * Una tasa nominal o mensual muy alta puede dar una TEA enorme que no cabe
     * en la columna. Se rechaza aquí, con un mensaje claro, en vez de dejar que
     * la base de datos falle con un error técnico.
     */
    private static void requireTeaWithinLimit(InterestRate rate, String name) {
        if (rate.effectiveAnnualRate().compareTo(MAX_TEA) > 0) {
            throw new BussinessRuleException(
                    "La tasa " + name + " pactada es demasiado alta: su TEA equivalente supera el máximo permitido");
        }
    }
}