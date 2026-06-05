package com.minduc.happabi.dto.response.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.ServiceOfferingType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceOfferingResponse {
    private UUID id;
    private String serviceCode;
    private ServiceOfferingType serviceType;
    private String groupName;
    private String serviceName;
    private String fitDescription;
    private String packageContent;
    private Integer durationMinutes;
    private Integer durationDays;
    private Long grossAmount;
    private Long platformFeeAmount;
    private Long nurseEarningAmount;
    private BigDecimal commissionRate;
    private Boolean isActive;
    private Integer sortOrder;
}
