package com.minduc.happabi.controller.payos;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.dto.response.payment.BookingPaymentLinkResponse;
import com.minduc.happabi.service.payment.IPayOsPaymentService;
import com.minduc.happabi.service.payment.IPayOsWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Payments", description = "APIs for handling payments and top-ups")
public class PayOsController {

    private final IPayOsPaymentService payOsPayment;

    @PostMapping("/create-topup-link")
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public ResponseEntity<BaseResponse<Map<String, String>>> createTopUpLink(@Valid @RequestBody TopUpRequest  topUpRequest) {
        String checkoutUrl = payOsPayment.createTopUpPaymentLink(topUpRequest);
        return ResponseEntity.ok(BaseResponse.ok(Map.of("checkoutUrl", checkoutUrl)));
    }

    @PostMapping("/nurse-deposit-link")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<Map<String, String>>> createNurseDepositPaymentLink() {
        String checkoutUrl = payOsPayment.createNurseDepositPaymentLink();
        return ResponseEntity.ok(BaseResponse.ok(Map.of("checkoutUrl", checkoutUrl)));
    }

    @PostMapping("/bookings/{bookingId}/payos-link")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<BookingPaymentLinkResponse>> createBookingPaymentLink(
            @PathVariable UUID bookingId) {
        return ResponseEntity.ok(BaseResponse.ok(
                "Booking payment link created.",
                payOsPayment.createBookingPaymentLink(bookingId)));
    }


}
