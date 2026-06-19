package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.entity.ServiceOffering;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceOfferingMapper {
    ServiceOfferingResponse toResponse(ServiceOffering serviceOffering);
}
