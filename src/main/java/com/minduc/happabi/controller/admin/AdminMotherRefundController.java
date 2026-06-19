package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.admin.ApproveMotherRefundRequest;
import com.minduc.happabi.dto.request.admin.RejectMotherRefundRequest;
import com.minduc.happabi.dto.response.booking.MotherRefundResponse;
import com.minduc.happabi.enums.MotherRefundStatus;
import com.minduc.happabi.service.booking.IMotherRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/mother-refund-requests")
@RequiredArgsConstructor
@Tag(name = "Admin Mother Refunds", description = "Admin manual mother refund workflow APIs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMotherRefundController {

    private final IMotherRefundService motherRefundService;

    @GetMapping
    @Operation(summary = "Get mother refund requests")
    public ResponseEntity<BaseResponse<Page<MotherRefundResponse>>> getRequests(
            @RequestParam(required = false) MotherRefundStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get refund requests successfully.",
                motherRefundService.getRefundRequests(status, pageable)));
    }

    @PostMapping(value = "/{requestId}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Approve mother refund request after manual bank transfer")
    public ResponseEntity<BaseResponse<MotherRefundResponse>> approve(
            @PathVariable UUID requestId,
            @Valid @ModelAttribute ApproveMotherRefundRequest request,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return ResponseEntity.ok(BaseResponse.ok("Refund request approved.",
                motherRefundService.approveRefundRequest(requestId, request, evidence)));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject mother refund request")
    public ResponseEntity<BaseResponse<MotherRefundResponse>> reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectMotherRefundRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Refund request rejected.",
                motherRefundService.rejectRefundRequest(requestId, request)));
    }
}
