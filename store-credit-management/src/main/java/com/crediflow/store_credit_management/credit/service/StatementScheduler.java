package com.crediflow.store_credit_management.credit.service;

import com.crediflow.store_credit_management.clients.domain.model.CreditCondition;
import com.crediflow.store_credit_management.clients.service.CreditConditionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Statement Scheduler
 * @summary
 * Tarea programada que genera los estados de cuenta al pasar el corte de cada
 * cliente (requisito del enunciado). Revisa periódicamente a todos los
 * clientes con condición vigente y emite lo que corresponda; como la
 * generación es idempotente, correr varias veces el mismo día no duplica nada.
 *
 * Está DESACTIVADA por defecto (crediflow.statements.scheduler-enabled=false)
 * para que en desarrollo no aparezcan estados de cuenta por sorpresa. Se activa
 * en producción. La generación manual no depende de esta tarea.
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crediflow.statements.scheduler-enabled", havingValue = "true")
class StatementScheduler {

    private final CreditConditionService creditConditionService;
    private final StatementService statementService;

    @Scheduled(
            fixedDelayString = "${crediflow.statements.scheduler-interval-ms:3600000}",
            initialDelayString = "${crediflow.statements.scheduler-initial-delay-ms:60000}")
    void generateDueStatements() {
        LocalDateTime now = LocalDateTime.now();
        int issued = 0;
        int failed = 0;
        for (CreditCondition condition : creditConditionService.listAllActive()) {
            Long storeId = condition.getClient().getStoreId();
            Long clientId = condition.getClient().getId();
            try {
                // Cada llamada es su propia transacción: un cliente con problemas no afecta a los demás.
                if (statementService.generateIfDue(storeId, clientId, now).isPresent()) {
                    issued++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("No se pudo generar el estado de cuenta del cliente {} (tienda {})",
                        clientId, storeId, e);
            }
        }
        if (issued > 0 || failed > 0) {
            log.info("Estados de cuenta: {} emitidos, {} con error", issued, failed);
        }
    }
}
