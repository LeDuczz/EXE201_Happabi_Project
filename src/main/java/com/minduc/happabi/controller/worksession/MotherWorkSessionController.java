package com.minduc.happabi.controller.worksession;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.worksession.ReportWorkSessionRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionResponse;
import com.minduc.happabi.service.worksession.IWorkSessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mothers/me/work-sessions")
@RequiredArgsConstructor
@Tag(name = "Mother Work Sessions", description = "Mother work session tracking and confirmation APIs")
@SecurityRequirement(name = "bearerAuth")
public class MotherWorkSessionController {

    private final IWorkSessionService workSessionService;

    @GetMapping
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<List<WorkSessionResponse>>> getMyWorkSessions() {
        return ResponseEntity.ok(BaseResponse.ok(workSessionService.getMyMotherWorkSessions()));
    }

    @GetMapping("/{workSessionId}")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> getMyWorkSession(
            @PathVariable UUID workSessionId) {
        return ResponseEntity.ok(BaseResponse.ok(workSessionService.getMyMotherWorkSession(workSessionId)));
    }

    @PostMapping("/{workSessionId}/confirm")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> confirm(
            @PathVariable UUID workSessionId) {
        return ResponseEntity.ok(BaseResponse.ok("Work session confirmed.",
                workSessionService.confirmByMother(workSessionId)));
    }

    @PostMapping("/{workSessionId}/report")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<WorkSessionResponse>> report(
            @PathVariable UUID workSessionId,
            @Valid @RequestBody ReportWorkSessionRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Work session reported.",
                workSessionService.reportByMother(workSessionId, request)));
    }
}
