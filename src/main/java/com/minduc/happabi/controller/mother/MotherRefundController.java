package com.minduc.happabi.controller.mother;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.booking.MotherRefundResponse;
import com.minduc.happabi.service.booking.IMotherRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mothers/me/refund-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MOTHER')")
public class MotherRefundController {

    private final IMotherRefundService motherRefundService;

    @GetMapping
    public ResponseEntity<BaseResponse<Page<MotherRefundResponse>>> getMine() {
        return ResponseEntity.ok(BaseResponse.ok("Get refund requests successfully.",
                motherRefundService.getMyRefundRequests(
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }
}
