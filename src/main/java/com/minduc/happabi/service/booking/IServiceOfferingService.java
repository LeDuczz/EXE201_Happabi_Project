package com.minduc.happabi.service.booking;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.enums.ServiceOfferingType;

import java.util.List;
import java.util.UUID;

public interface IServiceOfferingService {

    List<ServiceOfferingResponse> getActiveServices(ServiceOfferingType serviceType);

    ServiceOfferingResponse getActiveService(UUID serviceId);
}
