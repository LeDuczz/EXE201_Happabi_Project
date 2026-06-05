package com.minduc.happabi.controller.booking;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.service.booking.IServiceOfferingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-offerings")
@RequiredArgsConstructor
@Tag(name = "Service Offerings", description = "Happabi service catalog used by booking flow")
@SecurityRequirement(name = "bearerAuth")
public class ServiceOfferingController {

    private final IServiceOfferingService serviceOfferingService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<List<ServiceOfferingResponse>>> getActiveServices(
            @RequestParam(required = false) ServiceOfferingType type) {
        return ResponseEntity.ok(BaseResponse.ok(serviceOfferingService.getActiveServices(type)));
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<ServiceOfferingResponse>> getActiveService(
            @PathVariable UUID serviceId) {
        return ResponseEntity.ok(BaseResponse.ok(serviceOfferingService.getActiveService(serviceId)));
    }
}
