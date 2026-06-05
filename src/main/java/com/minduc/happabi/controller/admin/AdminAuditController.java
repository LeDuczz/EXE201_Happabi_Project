package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.service.audit.impl.AuditSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "Audit log management for administrators (ES-backed)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditSearchService auditSearchService;

    @GetMapping
    @Operation(summary = "Get all audit events from Elasticsearch with optional search")
    public ResponseEntity<BaseResponse<Page<Map<String, Object>>>> getAllAuditLogs(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(BaseResponse.ok("Get all audit logs from ES success",
                        auditSearchService.searchLogs(query, pageable)));
    }
}
