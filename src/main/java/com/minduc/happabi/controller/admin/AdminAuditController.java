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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "Audit log management for administrators (ES-backed)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditSearchService auditSearchService;

    @GetMapping
    @Operation(summary = "Get all audit events from Elasticsearch with optional search and date range")
    public ResponseEntity<BaseResponse<Page<Map<String, Object>>>> getAllAuditLogs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(BaseResponse.ok("Get all audit logs from ES success",
                        auditSearchService.searchLogs(query, fromDate, toDate, pageable)));
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get audit log search suggestions from Elasticsearch")
    public ResponseEntity<BaseResponse<List<String>>> getAuditSearchSuggestions(
            @RequestParam(required = false) String query) {
        return ResponseEntity
                .ok(BaseResponse.ok("Get audit log search suggestions success",
                        auditSearchService.suggestSearchTerms(query)));
    }
}
