package com.minduc.happabi.controller.worksession;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.worksession.CompleteChecklistItemRequest;
import com.minduc.happabi.dto.request.worksession.ReportWorkSessionIncidentRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionIncidentResponse;
import com.minduc.happabi.dto.response.worksession.WorkSessionResponse;
import com.minduc.happabi.service.worksession.IWorkSessionIncidentService;
import com.minduc.happabi.service.worksession.IWorkSessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nurses/me/work-sessions")
@RequiredArgsConstructor
@Tag(name = "Nurse Work Sessions", description = "Nurse work session check-in, checklist and checkout APIs")
@SecurityRequirement(name = "bearerAuth")
public class NurseWorkSessionController {

    private final IWorkSessionService workSessionService;
    private final IWorkSessionIncidentService incidentService;

    @GetMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<List<WorkSessionResponse>>> getMyWorkSessions() {
        return ResponseEntity.ok(BaseResponse.ok(workSessionService.getMyNurseWorkSessions()));
    }

    @GetMapping("/{workSessionId}")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> getMyWorkSession(
            @PathVariable UUID workSessionId) {
        return ResponseEntity.ok(BaseResponse.ok(workSessionService.getMyNurseWorkSession(workSessionId)));
    }

    @PostMapping(value = "/{workSessionId}/check-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> checkIn(
            @PathVariable UUID workSessionId,
            @RequestPart("images") List<MultipartFile> images) {
        return ResponseEntity.ok(BaseResponse.ok("Work session checked in.",
                workSessionService.checkIn(workSessionId, images)));
    }

    @PostMapping(value = "/{workSessionId}/checklist/{checklistItemId}/complete",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> completeChecklistItem(
            @PathVariable UUID workSessionId,
            @PathVariable UUID checklistItemId,
            @Valid @ModelAttribute CompleteChecklistItemRequest request,
            @RequestPart("images") List<MultipartFile> images) {
        return ResponseEntity.ok(BaseResponse.ok("Checklist item completed.",
                workSessionService.completeChecklistItem(workSessionId, checklistItemId, request, images)));
    }

    @PostMapping("/{workSessionId}/checklist/{checklistItemId}/undo")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> undoChecklistItem(
            @PathVariable UUID workSessionId,
            @PathVariable UUID checklistItemId) {
        return ResponseEntity.ok(BaseResponse.ok("Checklist item reverted.",
                workSessionService.undoChecklistItem(workSessionId, checklistItemId)));
    }

    @PostMapping("/{workSessionId}/checkout")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> checkout(
            @PathVariable UUID workSessionId) {
        return ResponseEntity.ok(BaseResponse.ok("Work session checked out.",
                workSessionService.checkout(workSessionId)));
    }

    @PostMapping(value = "/{workSessionId}/incidents/mother-unreachable", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<WorkSessionIncidentResponse>> reportMotherUnreachable(
            @PathVariable UUID workSessionId,
            @Valid @ModelAttribute ReportWorkSessionIncidentRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.ok(BaseResponse.ok("Incident reported.",
                incidentService.reportMotherUnreachable(workSessionId, request, images)));
    }
}
