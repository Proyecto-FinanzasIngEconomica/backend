package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.credit.domain.model.Payment;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseInstallment;
import com.crediflow.store_credit_management.credit.domain.model.Statement;
import com.crediflow.store_credit_management.credit.domain.model.StatementLine;
import com.crediflow.store_credit_management.credit.domain.model.StatementStatus;
import com.crediflow.store_credit_management.credit.dto.PaymentDtos.CreatePaymentRequest;
import com.crediflow.store_credit_management.credit.engine.InterestCalculator;
import com.crediflow.store_credit_management.credit.engine.PaymentAllocation;
import com.crediflow.store_credit_management.credit.repository.PaymentRepository;
import com.crediflow.store_credit_management.credit.repository.StatementRepository;
import com.crediflow.store_credit_management.shared.interfaces.rest.exception.BussinessRuleException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment Service
 * @summary
 * Registro y consulta de pagos. Un pago salda un estado de cuenta COMPLETO
 * (no hay pagos parciales): calcula la mora real de cada cuota con los días
 * reales de atraso, verifica que el monto recibido sea exacto, marca las cuotas
 * y el estado como pagados y guarda el desglose con la imputación de la
 * decisión #14 (mora, interés compensatorio, capital).
 *
 * Al quedar las cuotas en PAID, su capital deja de contar en la línea de
 * crédito: el cupo se libera solo, sin tocar ningún contador.
 *
 * Todos los métodos reciben el storeId de la tienda que opera y lo aplican en
 * cada consulta.
 *
 * @since 1.0.0
 */
@Service
public class PaymentService {

    private final ClientService clientService;
    private final StatementRepository statementRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(PlatformTransactionManager transactionManager,
                          ClientService clientService,
                          StatementRepository statementRepository,
                          PaymentRepository paymentRepository) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clientService = clientService;
        this.statementRepository = statementRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Paga un estado de cuenta completo. Si cualquier regla falla, no se guarda nada.
     *
     * Este método NO es transaccional a propósito: primero averigua el cliente
     * del estado (en una lectura aparte) para poder bloquearlo, y recién
     * entonces abre la transacción. Así el pago, el registro de compras y la
     * emisión de estados de cuenta del mismo cliente se procesan siempre uno
     * detrás de otro, y cada uno ve los datos ya confirmados por el anterior.
     */
    public Payment pay(Long storeId, Long statementId, CreatePaymentRequest request) {
        Long clientId = statementRepository.findByIdAndStoreId(statementId, storeId)
                .orElseThrow()
                .getClientId();
        return transactionTemplate.execute(status -> payLocked(storeId, clientId, statementId, request));
    }

    /** Busca un pago de la tienda; si no existe o es de otra, responde 404. */
    @Transactional(readOnly = true)
    public Payment get(Long storeId, Long paymentId) {
        return paymentRepository.findByIdAndStoreId(paymentId, storeId)
                .orElseThrow();
    }

    /** Lista paginada de pagos de la tienda; si clientId es null, trae los de todos. */
    @Transactional(readOnly = true)
    public Page<Payment> list(Long storeId, Long clientId, Pageable pageable) {
        return clientId == null
                ? paymentRepository.findByStoreId(storeId, pageable)
                : paymentRepository.findByStoreIdAndClientId(storeId, clientId, pageable);
    }

    // ------------------------------------------------------------------
    // Algoritmo (dentro de la transacción)
    // ------------------------------------------------------------------

    /**
     * Lo PRIMERO que hace es bloquear al cliente y recién después leer el
     * estado: así, si otro pago del mismo estado iba en camino, este lo ve ya
     * pagado y se rechaza en vez de cobrar dos veces.
     */
    private Payment payLocked(Long storeId, Long clientId, Long statementId, CreatePaymentRequest request) {
        Client client = clientService.getForUpdate(storeId, clientId);
        Statement statement = statementRepository.findByIdAndStoreId(statementId, storeId)
                .orElseThrow();

        requirePayable(statement);

        LocalDateTime paidAt = request.paidAt() != null ? request.paidAt() : LocalDateTime.now();
        if (paidAt.isBefore(statement.getIssuedAt())) {
            throw new BussinessRuleException(
                    "La fecha del pago no puede ser anterior a la emisión del estado de cuenta ("
                            + statement.getIssuedAt() + ")");
        }

        List<PurchaseInstallment> installments = new ArrayList<>();
        List<Payment.LineDraft> drafts = new ArrayList<>();
        BigDecimal totalDue = BigDecimal.ZERO;
        for (StatementLine line : statement.getLines()) {
            PurchaseInstallment installment = line.getInstallment();
            installments.add(installment);

            // Mora REAL: días entre el vencimiento y el pago, con la TEA moratoria de SU compra (decisión #6).
            int lateDays = InterestCalculator.lateDays(installment.getDueDate(), paidAt.toLocalDate());
            BigDecimal lateInterest = InterestCalculator.lateInterest(
                    installment.getAmount(), installment.getPurchase().getLateTea(), lateDays);

            // Imputación (decisión #14): primero la mora, luego el interés compensatorio, luego el capital.
            PaymentAllocation allocation = new PaymentAllocation(
                    lateInterest, installment.getInterestPart(), installment.getPrincipalPart());
            totalDue = totalDue.add(allocation.total());

            drafts.add(new Payment.LineDraft(
                    installment.getId(),
                    installment.getPurchase().getId(),
                    installment.getInstallmentNumber(),
                    installment.getDueDate(),
                    lateDays,
                    allocation.principal(),
                    allocation.compensatoryInterest(),
                    allocation.lateInterest()));
        }

        // No hay pagos parciales: el monto recibido debe ser exactamente el total a esa fecha.
        if (request.amount().compareTo(totalDue) != 0) {
            throw new BussinessRuleException("El monto no coincide con el total a pagar al "
                    + paidAt.toLocalDate() + ": " + statement.getCurrency() + " " + totalDue.toPlainString()
                    + ". No se admiten pagos parciales ni excedentes");
        }

        Payment payment = Payment.register(
                storeId, client.getId(), statement.getId(), paidAt, statement.getCurrency(), drafts);
        installments.forEach(installment -> installment.markPaid(paidAt));
        statement.markPaid();
        return paymentRepository.save(payment);
    }

    /** Solo el estado emitido (el más reciente) puede pagarse. */
    private static void requirePayable(Statement statement) {
        if (statement.getStatus() == StatementStatus.PAID) {
            throw new BussinessRuleException("Este estado de cuenta ya fue pagado");
        }
        if (statement.getStatus() == StatementStatus.SUPERSEDED) {
            throw new BussinessRuleException(
                    "Este estado de cuenta fue reemplazado por uno más reciente; debe pagarse el más reciente");
        }
    }
}