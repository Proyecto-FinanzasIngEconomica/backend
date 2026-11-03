package com.crediflow.store_credit_management.credit.controller;

import com.crediflow.store_credit_management.credit.dto.PaymentDtos.CreatePaymentRequest;
import com.crediflow.store_credit_management.credit.dto.PaymentDtos.PaymentResource;
import com.crediflow.store_credit_management.credit.dto.PaymentDtos.PaymentSummaryResource;
import com.crediflow.store_credit_management.credit.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment Controller
 * @summary
 * Endpoints REST de pagos. Solo traduce HTTP a llamadas de servicio y
 * entidades a Resources: no contiene reglas. Nunca devuelve entidades JPA.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Pagos de estados de cuenta")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/statements/{statementId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pagar un estado de cuenta completo",
            description = "No se admiten pagos parciales: el monto debe ser exactamente el total calculado a la "
                    + "fecha del pago, con la mora real de cada cuota. Solo puede pagarse el estado ISSUED más "
                    + "reciente. Al pagar, las cuotas y el estado pasan a PAID y el cupo se libera. "
                    + "Si el monto no coincide, el 422 indica el total correcto.")
    public PaymentResource pay(@PathVariable Long storeId,
                               @PathVariable Long statementId,
                               @Valid @RequestBody CreatePaymentRequest request) {
        return PaymentResource.from(paymentService.pay(storeId, statementId, request));
    }

    @GetMapping("/payments")
    @Operation(summary = "Listar pagos de la tienda",
            description = "Paginado. El filtro clientId es opcional. No incluye el detalle por cuota: "
                    + "para eso usa GET /payments/{paymentId}.")
    public PagedModel<PaymentSummaryResource> list(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long clientId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(
                paymentService.list(storeId, clientId, pageable).map(PaymentSummaryResource::from));
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Consultar un pago con su desglose por cuota",
            description = "Cada cuota muestra sus días de atraso reales y su imputación: mora, "
                    + "interés compensatorio y capital.")
    public PaymentResource get(@PathVariable Long storeId, @PathVariable Long paymentId) {
        return PaymentResource.from(paymentService.get(storeId, paymentId));
    }
}
