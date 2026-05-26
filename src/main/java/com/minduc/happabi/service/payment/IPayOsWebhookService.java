package com.minduc.happabi.service.payment;

import vn.payos.model.webhooks.Webhook;

public interface IPayOsWebhookService {

    String handlePayOsWebhook(Webhook webhookBody);

}
