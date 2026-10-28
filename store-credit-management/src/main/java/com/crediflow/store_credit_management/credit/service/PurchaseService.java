package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.catalog.domain.model.Product;
import com.crediflow.store_credit_management.catalog.service.ProductService;
import com.crediflow.store_credit_management.clients.domain.model.Client;
import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;
import com.crediflow.store_credit_management.clients.service.ClientService;
import com.crediflow.store_credit_management.clients.service.CreditConditionService;
import com.crediflow.store_credit_management.credit.domain.model.Purchase;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseItem;
import com.crediflow.store_credit_management.credit.domain.model.PurchaseModality;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.CreatePurchaseRequest;
import com.crediflow.store_credit_management.credit.dto.PurchaseDtos.PurchaseItemRequest;
import com.crediflow.store_credit_management.credit.engine.BillingCalendar;
import com.crediflow.store_credit_management.credit.engine.BillingCalendar.BillingCycle;
import com.crediflow.store_credit_management.credit.engine.InstallmentPlan;
import com.crediflow.store_credit_management.credit.engine.InstallmentScheduler;
import com.crediflow.store_credit_management.credit.engine.InterestCalculator;
import com.crediflow.store_credit_management.credit.repository.PurchaseRepository;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Service
 * @summary
 * Registro y consulta de compras al crédito. Aquí se juntan clientes,
 * catálogo y motor financiero: este servicio solo orquesta y valida reglas; los
 * cálculos viven en el motor y las invariantes en la entidad Purchase.
 *
 * Todos los métodos reciben el storeId de la tienda que opera y lo aplican en
 * cada consulta.
 *
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final ClientService clientService;
    private final CreditConditionService creditConditionService;
    private final CreditLineService creditLineService;
    private final ProductService productService;
    private final PurchaseRepository purchaseRepository;
    private final StatementRepository statementRepository;

    /**
     * Registra una compra. Si cualquier regla falla, no se guarda nada.
     *
     * Lo PRIMERO que hace es bloquear al cliente: así dos compras simultáneas
     * suyas se procesan una detrás de otra, y la segunda ve el cupo ya
     * descontado por la primera. No leas nada de la base antes de ese bloqueo.
     */
    @Transactional
    public Purchase register(Long storeId, CreatePurchaseRequest request) {
        Client client = clientService.getForUpdate(storeId, request.clientId());
        if (!client.isActive()) {
            throw new BussinessRuleException("No se pueden registrar compras a un cliente dado de baja");
        }
        CreditCondition condition = creditConditionService.getActive(storeId, client.getId());

        int installmentCount = resolveInstallmentCount(request, condition);
        LocalDateTime purchasedAt = request.purchasedAt() != null ? request.purchasedAt() : LocalDateTime.now();

        List<Purchase.ItemDraft> items = buildItems(storeId, request, condition);
        BigDecimal itemsTotal = items.stream()
                .map(item -> PurchaseItem.subtotalOf(item.quantity(), item.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemsTotal.signum() <= 0) {
            throw new BussinessRuleException("El total de la compra debe ser mayor a cero");
        }

        BillingCycle cycle = BillingCalendar.cycleFor(
                purchasedAt, condition.getCutoffDay(), condition.getCutoffTime(), condition.getPaymentDay());
        LocalDate purchaseDate = purchasedAt.toLocalDate();
        int graceDays = InterestCalculator.daysBetween(purchaseDate, cycle.paymentDate());

        List<Purchase.InstallmentDraft> installments = request.modality() == PurchaseModality.END_OF_MONTH
                ? endOfMonthInstallment(itemsTotal, condition.compensatoryTea(), purchaseDate, cycle.paymentDate())
                : financedInstallments(itemsTotal, condition.compensatoryTea(), purchaseDate,
                        cycle.paymentDate(), condition.getPaymentDay(), installmentCount);
        
        LocalDate firstDueDate = installments.get(0).dueDate();
        if (statementRepository.existsByClientIdAndPaymentDateGreaterThanEqual(client.getId(), firstDueDate)) {
            throw new BussinessRuleException("No se puede registrar la compra: su primera cuota vence el "
                    + firstDueDate + " y ese ciclo ya fue facturado en un estado de cuenta");
        }

        Purchase purchase = Purchase.register(
                storeId,
                client.getId(),
                condition.getId(),
                purchasedAt,
                request.modality(),
                installmentCount,
                condition.currency(),
                condition.compensatoryTea(),
                condition.lateTea(),
                cycle.cutoffDate(),
                cycle.paymentDate(),
                graceDays,
                items,
                installments);

        BigDecimal available = creditLineService.availableAmount(storeId, client.getId(), condition);
        if (purchase.getFinancedAmount().compareTo(available) > 0) {
            throw new BussinessRuleException("Línea de crédito insuficiente: la compra financia "
                    + condition.currency() + " " + purchase.getFinancedAmount().toPlainString()
                    + " y el cupo disponible es "
                    + condition.currency() + " " + available.max(BigDecimal.ZERO).toPlainString());
        }
        return purchaseRepository.save(purchase);
    }

    /** Busca una compra de la tienda; si no existe o es de otra, responde 404. */
    @Transactional(readOnly = true)
    public Purchase get(Long storeId, Long purchaseId) {
        return purchaseRepository.findByIdAndStoreId(purchaseId, storeId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Compra", purchaseId.toString()));
    }

    /** Lista paginada de compras de la tienda; si clientId es null, trae las de todos. */
    @Transactional(readOnly = true)
    public Page<Purchase> list(Long storeId, Long clientId, Pageable pageable) {
        return clientId == null
                ? purchaseRepository.findByStoreId(storeId, pageable)
                : purchaseRepository.findByStoreIdAndClientId(storeId, clientId, pageable);
    }

    // ------------------------------------------------------------------
    // Reglas y armado
    // ------------------------------------------------------------------

    /** 1 cuota para fin de mes; N para cuotas, sin pasar el máximo de la condición. */
    private static int resolveInstallmentCount(CreatePurchaseRequest request, CreditCondition condition) {
        if (request.modality() == PurchaseModality.END_OF_MONTH) {
            if (request.installmentCount() != null) {
                throw new IllegalArgumentException("Una compra a fin de mes no lleva número de cuotas");
            }
            return 1;
        }
        Integer count = request.installmentCount();
        if (count == null) {
            throw new IllegalArgumentException("Una compra en cuotas requiere el número de cuotas");
        }
        if (count > condition.getMaxInstallmentMonths()) {
            throw new BussinessRuleException("Este cliente puede financiar hasta "
                    + condition.getMaxInstallmentMonths() + " cuotas");
        }
        return count;
    }

    /** Valida cada producto contra la tienda y la condición, y copia nombre, unidad y precio de crédito. */
    private List<Purchase.ItemDraft> buildItems(Long storeId, CreatePurchaseRequest request, CreditCondition condition) {
        List<Purchase.ItemDraft> drafts = new ArrayList<>();
        for (PurchaseItemRequest line : request.items()) {
            Product product = productService.get(storeId, line.productId());
            if (!product.isActive()) {
                throw new BussinessRuleException(
                        "El producto '" + product.getName() + "' está dado de baja y no se puede vender");
            }
            if (product.getCurrency() != condition.currency()) {
                throw new BussinessRuleException("El producto '" + product.getName() + "' está en "
                        + product.getCurrency() + " y el crédito del cliente es en " + condition.currency());
            }
            boolean allowed = request.modality() == PurchaseModality.END_OF_MONTH
                    ? product.isEndOfMonthAllowed()
                    : product.isInstallmentsAllowed();
            if (!allowed) {
                throw new BussinessRuleException("El producto '" + product.getName() + "' no admite "
                        + (request.modality() == PurchaseModality.END_OF_MONTH ? "pago a fin de mes" : "pago en cuotas"));
            }
            drafts.add(new Purchase.ItemDraft(
                    product.getId(),
                    product.getName(),
                    product.getUnitOfMeasure(),
                    line.quantity(),
                    product.getCreditPrice()));
        }
        return drafts;
    }

    /** Fin de mes: una sola cuota con el capital más el interés de compra a pago. */
    private static List<Purchase.InstallmentDraft> endOfMonthInstallment(BigDecimal itemsTotal,
                                                                         BigDecimal tea,
                                                                         LocalDate purchaseDate,
                                                                         LocalDate paymentDate) {
        BigDecimal interest = InterestCalculator.endOfMonthInterest(itemsTotal, tea, purchaseDate, paymentDate);
        return List.of(new Purchase.InstallmentDraft(
                1, paymentDate, itemsTotal.add(interest), itemsTotal, interest));
    }

    /** Cuotas: cronograma francés con la gracia capitalizada, tal como lo devuelve el motor. */
    private static List<Purchase.InstallmentDraft> financedInstallments(BigDecimal itemsTotal,
                                                                        BigDecimal tea,
                                                                        LocalDate purchaseDate,
                                                                        LocalDate cyclePaymentDate,
                                                                        int paymentDay,
                                                                        int installmentCount) {
        InstallmentPlan plan = InstallmentScheduler.generate(
                itemsTotal, tea, purchaseDate, cyclePaymentDate, paymentDay, installmentCount);
        return plan.installments().stream()
                .map(row -> new Purchase.InstallmentDraft(
                        row.number(), row.dueDate(), row.payment(), row.principal(), row.interest()))
                .toList();
    }
}
