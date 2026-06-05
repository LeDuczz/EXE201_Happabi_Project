package com.minduc.happabi.repository;

import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.enums.ServiceOfferingType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    Optional<ServiceOffering> findByServiceCode(String serviceCode);

    List<ServiceOffering> findByIsActiveTrueOrderBySortOrderAscServiceNameAsc();

    List<ServiceOffering> findByServiceTypeAndIsActiveTrueOrderBySortOrderAscServiceNameAsc(ServiceOfferingType serviceType);
}
