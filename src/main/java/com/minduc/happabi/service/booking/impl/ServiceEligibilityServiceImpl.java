package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.dto.response.nurse.NurseSkillResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseSkillEntity;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.ServiceOfferingRequiredSkill;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NurseSkill;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.mapper.ServiceOfferingMapper;
import com.minduc.happabi.repository.NurseSkillRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.repository.ServiceOfferingRequiredSkillRepository;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.nurse.NurseSkillCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceEligibilityServiceImpl implements IServiceEligibilityService {

    private final NurseSkillRepository nurseSkillRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ServiceOfferingRequiredSkillRepository requiredSkillRepository;
    private final ServiceOfferingMapper serviceOfferingMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> getEligibleServices(NurseProfile nurseProfile, ServiceOfferingType serviceType) {
        List<ServiceOffering> services = serviceType == null
                ? serviceOfferingRepository.findByIsActiveTrueOrderBySortOrderAscServiceNameAsc()
                : serviceOfferingRepository.findByServiceTypeAndIsActiveTrueOrderBySortOrderAscServiceNameAsc(serviceType);
        Set<NurseSkill> verifiedSkills = getVerifiedSkillSet(nurseProfile);
        Map<ServiceOffering, Set<NurseSkill>> requiredSkills = requiredSkillRepository.findByServiceOfferingIn(services)
                .stream()
                .collect(Collectors.groupingBy(
                        ServiceOfferingRequiredSkill::getServiceOffering,
                        Collectors.mapping(ServiceOfferingRequiredSkill::getSkill, Collectors.toSet())
                ));

        return services.stream()
                .filter(service -> isEligible(requiredSkills.getOrDefault(service, Set.of()), verifiedSkills))
                .map(serviceOfferingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEligibleForService(NurseProfile nurseProfile, ServiceOffering serviceOffering) {
        Set<NurseSkill> requiredSkills = requiredSkillRepository.findByServiceOffering(serviceOffering)
                .stream()
                .map(ServiceOfferingRequiredSkill::getSkill)
                .collect(Collectors.toSet());
        return isEligible(requiredSkills, getVerifiedSkillSet(nurseProfile));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<NurseSkill> getVerifiedSkillSet(NurseProfile nurseProfile) {
        return nurseSkillRepository.findByNurseAndVerifiedAtIsNotNullOrderBySkillAsc(nurseProfile)
                .stream()
                .map(NurseSkillEntity::getSkill)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NurseSkillResponse> getNurseSkills(NurseProfile nurseProfile, boolean verifiedOnly) {
        List<NurseSkillEntity> skills = verifiedOnly
                ? nurseSkillRepository.findByNurseAndVerifiedAtIsNotNullOrderBySkillAsc(nurseProfile)
                : nurseSkillRepository.findByNurseOrderBySkillAsc(nurseProfile);
        return skills.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void verifyDeclaredSkills(NurseProfile nurseProfile, User actor) {
        OffsetDateTime now = OffsetDateTime.now();
        nurseSkillRepository.findByNurseOrderBySkillAsc(nurseProfile).forEach(skill -> {
            if (skill.getVerifiedAt() == null) {
                skill.setVerifiedAt(now);
                skill.setVerifiedBy(actor);
                nurseSkillRepository.save(skill);
            }
        });
    }

    @Override
    @Transactional
    public void replaceDeclaredSkills(NurseProfile nurseProfile, Collection<NurseSkill> skills) {
        Set<NurseSkill> nextSkills = skills == null ? Set.of() : new HashSet<>(skills);
        if (nextSkills.isEmpty()) {
            nurseSkillRepository.deleteAll(nurseSkillRepository.findByNurseOrderBySkillAsc(nurseProfile));
            return;
        }

        nurseSkillRepository.deleteByNurseAndSkillNotIn(nurseProfile, nextSkills);
        Map<NurseSkill, NurseSkillEntity> existing = nurseSkillRepository.findByNurseAndSkillIn(nurseProfile, nextSkills)
                .stream()
                .collect(Collectors.toMap(NurseSkillEntity::getSkill, Function.identity()));
        nextSkills.forEach(skill -> existing.computeIfAbsent(skill, item -> nurseSkillRepository.save(NurseSkillEntity.builder()
                .nurse(nurseProfile)
                .skill(item)
                .build())));
    }

    private boolean isEligible(Set<NurseSkill> requiredSkills, Set<NurseSkill> verifiedSkills) {
        return !requiredSkills.isEmpty() && verifiedSkills.containsAll(requiredSkills);
    }

    private NurseSkillResponse toResponse(NurseSkillEntity entity) {
        return NurseSkillResponse.builder()
                .skill(entity.getSkill())
                .label(NurseSkillCatalog.label(entity.getSkill()))
                .groupName(NurseSkillCatalog.groupName(entity.getSkill()))
                .verified(entity.getVerifiedAt() != null)
                .verifiedAt(entity.getVerifiedAt())
                .build();
    }
}
