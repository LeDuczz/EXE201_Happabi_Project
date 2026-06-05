package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.mapper.ServiceOfferingMapper;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.service.booking.IServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceOfferingServiceImpl implements IServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceOfferingMapper serviceOfferingMapper;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ServiceOfferingResponse> getActiveServices(ServiceOfferingType serviceType) {
        List<ServiceOffering> services = serviceType == null
                ? serviceOfferingRepository.findByIsActiveTrueOrderBySortOrderAscServiceNameAsc()
                : serviceOfferingRepository.findByServiceTypeAndIsActiveTrueOrderBySortOrderAscServiceNameAsc(serviceType);
        return services.stream()
                .map(serviceOfferingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ServiceOfferingResponse getActiveService(UUID serviceId) {
        ServiceOffering service = serviceOfferingRepository.findById(serviceId)
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new AppException(BookingErrorCode.SERVICE_OFFERING_NOT_FOUND));
        return serviceOfferingMapper.toResponse(service);
    }
}
