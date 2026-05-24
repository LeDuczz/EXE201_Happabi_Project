package com.minduc.happabi.service.payment;

import com.minduc.happabi.dto.request.nurse.TopUpRequest;

public interface IPayOsPaymentService {

    String createTopUpPaymentLink(String nurseId, TopUpRequest request);

}
