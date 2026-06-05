package com.minduc.happabi.service.mother;

import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.enums.NurseSpecialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IMotherNurseProfileService {

    Page<NursePublicProfileResponse> searchActiveNurses(String keyword,
                                                        String city,
                                                        NurseSpecialty specialty,
                                                        Boolean availableOnly,
                                                        Pageable pageable);

    NursePublicProfileResponse getActiveNurse(UUID profileId);
}
