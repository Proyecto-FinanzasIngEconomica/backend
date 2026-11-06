package com.crediflow.store_credit_management.audit.controller;

import com.crediflow.store_credit_management.audit.domain.model.AuditAction;
import com.crediflow.store_credit_management.audit.dto.AuditDtos.AuditEventResource;
import com.crediflow.store_credit_management.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/api/v1/audit")
    public PagedModel<AuditEventResource> search(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(sort = {"occurredAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return new PagedModel<>(auditService.search(storeId, action, userId, from, to, pageable)
                .map(AuditEventResource::from));
    }
}
