package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceEligibilityServiceImplTest {

    @Mock
    private NurseSkillRepository nurseSkillRepository;

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @Mock
    private ServiceOfferingRequiredSkillRepository requiredSkillRepository;

    @Mock
    private ServiceOfferingMapper serviceOfferingMapper;

    @InjectMocks
    private ServiceEligibilityServiceImpl service;

    private final NurseProfile nurse = NurseProfile.builder().id(UUID.randomUUID()).build();

    @Test
    void isEligibleForServiceReturnsTrueWhenNurseHasAllRequiredVerifiedSkills() {
        ServiceOffering offering = ServiceOffering.builder().id(UUID.randomUUID()).build();
        when(requiredSkillRepository.findByServiceOffering(offering)).thenReturn(List.of(
                required(offering, NurseSkill.NEWBORN_BATHING),
                required(offering, NurseSkill.NEWBORN_BASIC_CARE)));
        when(nurseSkillRepository.findByNurseAndVerifiedAtIsNotNullOrderBySkillAsc(nurse)).thenReturn(List.of(
                verified(NurseSkill.NEWBORN_BATHING),
                verified(NurseSkill.NEWBORN_BASIC_CARE),
                verified(NurseSkill.CUSTOMER_CARE)));

        assertThat(service.isEligibleForService(nurse, offering)).isTrue();
    }

    @Test
    void isEligibleForServiceReturnsFalseWhenOfferingHasNoRequiredSkills() {
        ServiceOffering offering = ServiceOffering.builder().id(UUID.randomUUID()).build();
        when(requiredSkillRepository.findByServiceOffering(offering)).thenReturn(List.of());
        when(nurseSkillRepository.findByNurseAndVerifiedAtIsNotNullOrderBySkillAsc(nurse)).thenReturn(List.of(
                verified(NurseSkill.NEWBORN_BATHING)));

        assertThat(service.isEligibleForService(nurse, offering)).isFalse();
    }

    @Test
    void getEligibleServicesFiltersOutServicesWithMissingRequiredSkills() {
        ServiceOffering bath = ServiceOffering.builder().id(UUID.randomUUID()).serviceName("Bath").build();
        ServiceOffering massage = ServiceOffering.builder().id(UUID.randomUUID()).serviceName("Massage").build();
        ServiceOfferingResponse response = ServiceOfferingResponse.builder().id(bath.getId()).serviceName("Bath").build();
        when(serviceOfferingRepository.findByServiceTypeAndIsActiveTrueOrderBySortOrderAscServiceNameAsc(ServiceOfferingType.SINGLE))
                .thenReturn(List.of(bath, massage));
        when(nurseSkillRepository.findByNurseAndVerifiedAtIsNotNullOrderBySkillAsc(nurse)).thenReturn(List.of(
                verified(NurseSkill.NEWBORN_BATHING)));
        when(requiredSkillRepository.findByServiceOfferingIn(List.of(bath, massage))).thenReturn(List.of(
                required(bath, NurseSkill.NEWBORN_BATHING),
                required(massage, NurseSkill.POSTPARTUM_RECOVERY_MASSAGE)));
        when(serviceOfferingMapper.toResponse(bath)).thenReturn(response);

        assertThat(service.getEligibleServices(nurse, ServiceOfferingType.SINGLE)).containsExactly(response);
        verify(serviceOfferingMapper, never()).toResponse(massage);
    }

    @Test
    void replaceDeclaredSkillsDeletesAllWhenNextSkillSetIsEmpty() {
        NurseSkillEntity existing = NurseSkillEntity.builder().skill(NurseSkill.CUSTOMER_CARE).build();
        when(nurseSkillRepository.findByNurseOrderBySkillAsc(nurse)).thenReturn(List.of(existing));

        service.replaceDeclaredSkills(nurse, Set.of());

        verify(nurseSkillRepository).deleteAll(List.of(existing));
    }

    @Test
    void verifyDeclaredSkillsStampsOnlyUnverifiedSkills() {
        User actor = User.builder().id(UUID.randomUUID()).build();
        NurseSkillEntity unverified = NurseSkillEntity.builder().skill(NurseSkill.CUSTOMER_CARE).build();
        NurseSkillEntity verified = verified(NurseSkill.NEWBORN_BATHING);
        when(nurseSkillRepository.findByNurseOrderBySkillAsc(nurse)).thenReturn(List.of(unverified, verified));

        service.verifyDeclaredSkills(nurse, actor);

        ArgumentCaptor<NurseSkillEntity> captor = ArgumentCaptor.forClass(NurseSkillEntity.class);
        verify(nurseSkillRepository).save(captor.capture());
        assertThat(captor.getValue().getSkill()).isEqualTo(NurseSkill.CUSTOMER_CARE);
        assertThat(captor.getValue().getVerifiedBy()).isEqualTo(actor);
        assertThat(captor.getValue().getVerifiedAt()).isNotNull();
    }

    private NurseSkillEntity verified(NurseSkill skill) {
        return NurseSkillEntity.builder()
                .nurse(nurse)
                .skill(skill)
                .verifiedAt(OffsetDateTime.now())
                .build();
    }

    private ServiceOfferingRequiredSkill required(ServiceOffering offering, NurseSkill skill) {
        return ServiceOfferingRequiredSkill.builder()
                .serviceOffering(offering)
                .skill(skill)
                .build();
    }
}
