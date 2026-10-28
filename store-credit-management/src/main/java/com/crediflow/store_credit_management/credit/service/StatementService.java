package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.clients.service.CreditConditionService;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseInstallment;
import com.crediflow.store_credit_management.credit.domain.model.Statement;
import com.crediflow.store_credit_management.credit.domain.model.StatementStatus;
import com.crediflow.store_credit_management.credit.engine.BillingCalendar;
import com.crediflow.store_credit_management.credit.engine.BillingCalendar.BillingCycle;
import com.crediflow.store_credit_management.credit.engine.InterestCalculator;
import com.crediflow.store_credit_management.credit.repository.PurchaseInstallmentRepository;
import com.crediflow.store_credit_management.credit.repository.StatementRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Statement Service
 * @summary
 * Generación y consulta de estados de cuenta. Un estado reúne lo que el
 * cliente debe pagar en la fecha de pago de un ciclo: las cuotas que vencen
 * ese día y las vencidas arrastradas (decisión #10), con la mora proyectada a
 * esa fecha. Los cálculos viven en el motor y las invariantes en la entidad;
 * aquí solo se decide qué incluir y se valida que emitirlo tenga sentido.
 *
 * Hay dos formas de generar: manual (responde con un error si no hay nada que
 * emitir, para que se entienda por qué) y programada (si no hay nada, calla).
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private final ClientService clientService;
    private final CreditConditionService creditConditionService;
    private final PurchaseInstallmentRepository installmentRepository;
    private final StatementRepository statementRepository;

    /**
     * Generación manual para un cliente. Factura el último ciclo cuyo corte ya
     * pasó a la fecha {@code asOf} (la hora actual si es null). Si no hay nada
     * que emitir, responde 422 con la razón.
     */
    @Transactional
    public Statement generate(Long storeId, Long clientId, LocalDateTime asOf) {
        Attempt attempt = attempt(storeId, clientId, asOf != null ? asOf : LocalDateTime.now());
        if (attempt.statement() == null) {
            throw new BussinessRuleException(attempt.reason());
        }
        return attempt.statement();
    }

    /**
     * Generación para la tarea programada: devuelve vacío, sin error, si el
     * ciclo ya se emitió o el cliente no tiene nada que cobrar. Cada llamada es
     * su propia transacción, así que un cliente con problemas no afecta a otro.
     */
    @Transactional
    public Optional<Statement> generateIfDue(Long storeId, Long clientId, LocalDateTime asOf) {
        return Optional.ofNullable(attempt(storeId, clientId, asOf).statement());
    }

    /** Busca un estado de la tienda; si no existe o es de otra, responde 404. */
    @Transactional(readOnly = true)
    public Statement get(Long storeId, Long statementId) {
        return statementRepository.findByIdAndStoreId(statementId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el estado de cuenta " + statementId));
    }

    /** Lista paginada de estados de la tienda; si clientId es null, trae los de todos. */
    @Transactional(readOnly = true)
    public Page<Statement> list(Long storeId, Long clientId, Pageable pageable) {
        return clientId == null
                ? statementRepository.findByStoreId(storeId, pageable)
                : statementRepository.findByStoreIdAndClientId(storeId, clientId, pageable);
    }

    // ------------------------------------------------------------------
    // Algoritmo
    // ------------------------------------------------------------------

    /**
     * Intenta emitir el estado del último ciclo cerrado del cliente.
     *
     * Bloquea al cliente igual que el registro de compras: así una compra y un
     * estado de cuenta del mismo cliente no pueden cruzarse. No leas nada de la
     * base antes de ese bloqueo.
     */
    private Attempt attempt(Long storeId, Long clientId, LocalDateTime asOf) {
        Client client = clientService.getForUpdate(storeId, clientId);
        CreditCondition condition = creditConditionService.getActive(storeId, client.getId());

        BillingCycle cycle = BillingCalendar.lastClosedCycle(
                asOf, condition.getCutoffDay(), condition.getCutoffTime(), condition.getPaymentDay());

        if (statementRepository.existsByClientIdAndCycleCutoffDate(client.getId(), cycle.cutoffDate())) {
            return Attempt.skipped("Ya existe el estado de cuenta del ciclo con corte el " + cycle.cutoffDate());
        }
        // Emitir un ciclo anterior pasaría a SUPERSEDED a un estado más reciente.
        if (statementRepository.existsByClientIdAndPaymentDateGreaterThanEqual(client.getId(), cycle.paymentDate())) {
            return Attempt.skipped("Ya existe un estado de cuenta de un ciclo posterior; "
                    + "no se pueden emitir estados de ciclos anteriores");
        }

        List<PurchaseInstallment> due = installmentRepository.pendingDueUpTo(
                storeId, client.getId(), cycle.paymentDate());
        if (due.isEmpty()) {
            return Attempt.skipped("El cliente no tiene cuotas por cobrar hasta el " + cycle.paymentDate());
        }

        List<Statement.LineDraft> drafts = due.stream()
                .map(installment -> toDraft(installment, cycle.paymentDate()))
                .toList();
        Statement statement = Statement.register(
                storeId,
                client.getId(),
                cycle.cutoffDate(),
                cycle.paymentDate(),
                asOf,
                condition.currency(),
                drafts);

        // Las cuotas sin pagar de estados anteriores ya van en este: ellos quedan como historial.
        statementRepository.findByClientIdAndStatus(client.getId(), StatementStatus.ISSUED)
                .forEach(Statement::supersede);

        return Attempt.issued(statementRepository.save(statement));
    }

    /**
     * Mora proyectada de una cuota hasta la fecha de pago del ciclo: simple,
     * sobre el monto de la cuota, con la TEA moratoria de SU compra (decisión #6).
     * Las cuotas que no están vencidas dan 0 días y 0 de mora.
     */
    private static Statement.LineDraft toDraft(PurchaseInstallment installment, LocalDate paymentDate) {
        int lateDays = InterestCalculator.lateDays(installment.getDueDate(), paymentDate);
        BigDecimal lateInterest = InterestCalculator.lateInterest(
                installment.getAmount(), installment.getPurchase().getLateTea(), lateDays);
        return new Statement.LineDraft(installment, lateDays, lateInterest);
    }

    /** Resultado de un intento: el estado emitido, o la razón por la que no se emitió. */
    private record Attempt(Statement statement, String reason) {

        static Attempt issued(Statement statement) {
            return new Attempt(statement, null);
        }

        static Attempt skipped(String reason) {
            return new Attempt(null, reason);
        }
    }
}
