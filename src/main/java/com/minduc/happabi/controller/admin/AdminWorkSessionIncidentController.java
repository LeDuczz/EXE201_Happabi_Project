package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.admin.ReviewWorkSessionIncidentRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionIncidentResponse;
import com.minduc.happabi.enums.WorkSessionIncidentStatus;
import com.minduc.happabi.service.worksession.IWorkSessionIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/work-session-incidents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWorkSessionIncidentController {

    private final IWorkSessionIncidentService incidentService;

    @GetMapping
    public ResponseEntity<BaseResponse<Page<WorkSessionIncidentResponse>>> getIncidents(
            @RequestParam(required = false) WorkSessionIncidentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get work session incidents successfully.",
                incidentService.getIncidents(status, pageable)));
    }

    @PostMapping("/{incidentId}/approve")
    public ResponseEntity<BaseResponse<WorkSessionIncidentResponse>> approve(
            @PathVariable UUID incidentId,
            @Valid @RequestBody(required = false) ReviewWorkSessionIncidentRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Incident approved.",
                incidentService.approveIncident(incidentId, request)));
    }

    @PostMapping("/{incidentId}/reject")
    public ResponseEntity<BaseResponse<WorkSessionIncidentResponse>> reject(
            @PathVariable UUID incidentId,
            @Valid @RequestBody(required = false) ReviewWorkSessionIncidentRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Incident rejected.",
                incidentService.rejectIncident(incidentId, request)));
    }

    @PostMapping("/work-sessions/{workSessionId}/nurse-no-show")
    public ResponseEntity<BaseResponse<WorkSessionIncidentResponse>> markNurseNoShow(
            @PathVariable UUID workSessionId,
            @Valid @RequestBody(required = false) ReviewWorkSessionIncidentRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Nurse no-show penalty applied.",
                incidentService.markNurseNoShow(workSessionId, request)));
    }}
