package com.crediflow.store_credit_management.credit.controller;

import com.crediflow.store_credit_management.credit.dto.StatementDtos.GenerateStatementRequest;
import com.crediflow.store_credit_management.credit.dto.StatementDtos.StatementResource;
import com.crediflow.store_credit_management.credit.dto.StatementDtos.StatementSummaryResource;
import com.crediflow.store_credit_management.credit.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.LocalDateTime;

/**
 * Statement Controller
 * @summary
 * Endpoints REST de estados de cuenta. Solo traduce HTTP a llamadas de
 * servicio y entidades a Resources: no contiene reglas. Nunca devuelve
 * entidades JPA.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
@RequiredArgsConstructor
@Tag(name = "Statements", description = "Estados de cuenta por ciclo de facturación")
public class StatementController {

    private final StatementService statementService;

    @PostMapping("/clients/{clientId}/statements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generar el estado de cuenta de un cliente",
            description = "Factura el último ciclo cuyo corte ya pasó. El cuerpo es opcional: con asOf se "
                    + "simula otra fecha (para demostraciones); sin él se usa la hora actual. Los ciclos "
                    + "deben generarse en orden cronológico. Responde 422 si no hay nada que emitir.")
    public StatementResource generate(@PathVariable Long storeId,
                                      @PathVariable Long clientId,
                                      @RequestBody(required = false) GenerateStatementRequest request) {
        LocalDateTime asOf = request == null ? null : request.asOf();
        return StatementResource.from(statementService.generate(storeId, clientId, asOf));
    }

    @GetMapping("/statements")
    @Operation(summary = "Listar estados de cuenta de la tienda",
            description = "Paginado. El filtro clientId es opcional. No incluye las cuotas: "
                    + "para el detalle usa GET /statements/{statementId}.")
    public PagedModel<StatementSummaryResource> list(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long clientId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(
                statementService.list(storeId, clientId, pageable).map(StatementSummaryResource::from));
    }

    @GetMapping("/statements/{statementId}")
    @Operation(summary = "Consultar un estado de cuenta con sus cuotas",
            description = "Las cuotas salen ordenadas por fecha de compra; las vencidas arrastradas llevan "
                    + "overdue = true y su mora proyectada a la fecha de pago.")
    public StatementResource get(@PathVariable Long storeId, @PathVariable Long statementId) {
        return StatementResource.from(statementService.get(storeId, statementId));
    }
}
