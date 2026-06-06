package com.minduc.happabi.service.booking;

import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import com.minduc.happabi.dto.response.nurse.NurseSkillResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.enums.NurseSkill;
import com.minduc.happabi.enums.ServiceOfferingType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IServiceEligibilityService {
    List<ServiceOfferingResponse> getEligibleServices(NurseProfile nurseProfile, ServiceOfferingType serviceType);

    boolean isEligibleForService(NurseProfile nurseProfile, ServiceOffering serviceOffering);

    Set<NurseSkill> getVerifiedSkillSet(NurseProfile nurseProfile);

    List<NurseSkillResponse> getNurseSkills(NurseProfile nurseProfile, boolean verifiedOnly);

    void verifyDeclaredSkills(NurseProfile nurseProfile, com.minduc.happabi.entity.User actor);

    void replaceDeclaredSkills(NurseProfile nurseProfile, Collection<NurseSkill> skills);
}
