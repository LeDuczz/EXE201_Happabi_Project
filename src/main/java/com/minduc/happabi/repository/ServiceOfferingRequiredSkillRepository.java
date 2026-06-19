package com.minduc.happabi.repository;

import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.ServiceOfferingRequiredSkill;
import com.minduc.happabi.enums.NurseSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOfferingRequiredSkillRepository extends JpaRepository<ServiceOfferingRequiredSkill, UUID> {
    List<ServiceOfferingRequiredSkill> findByServiceOffering(ServiceOffering serviceOffering);

    List<ServiceOfferingRequiredSkill> findByServiceOfferingIn(Collection<ServiceOffering> serviceOfferings);

    Optional<ServiceOfferingRequiredSkill> findByServiceOfferingAndSkill(ServiceOffering serviceOffering, NurseSkill skill);

    void deleteByServiceOfferingAndSkillNotIn(ServiceOffering serviceOffering, Collection<NurseSkill> skills);
}
