package com.minduc.happabi.controller.payos;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.service.payment.IPayOsWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

@RestController
@RequestMapping("/api/v1/webhook/payos")
@RequiredArgsConstructor
public class PayOsWebhookController {

    private final IPayOsWebhookService payOsWebhookService;

    @PostMapping
    public ResponseEntity<BaseResponse<String>> handlePayOsWebhook(@RequestBody Webhook webhookBody) {
        String response = payOsWebhookService.handlePayOsWebhook(webhookBody);
        return ResponseEntity.ok(BaseResponse.ok(response));
    }
}
