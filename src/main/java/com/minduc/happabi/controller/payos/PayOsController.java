package com.minduc.happabi.controller.payos;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.service.payment.IPayOsPaymentService;
import com.minduc.happabi.service.payment.IPayOsWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Payments", description = "APIs for handling payments and top-ups")
public class PayOsController {

    private final IPayOsWebhookService payOsWebhook;
    private final IPayOsPaymentService payOsPayment;




//    @PreAuthorize("hasRole('NURSE') AND hasAuthority('')")
    @PostMapping("/create-topup-link")
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public ResponseEntity<BaseResponse<Map<String, String>>> createTopUpLink(@Valid @RequestBody TopUpRequest  topUpRequest) {
        String nurseId = AuthUtils.getCurrentUserId();
        String checkoutUrl = payOsPayment.createTopUpPaymentLink(nurseId, topUpRequest);
        return ResponseEntity.ok(BaseResponse.ok(Map.of("checkoutUrl", checkoutUrl)));
    }



}
