package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.request.nurse.CreateNurseAvailabilityWindowRequest;
import com.minduc.happabi.dto.response.nurse.NurseAvailabilityWindowResponse;

import java.util.List;
import java.util.UUID;

public interface INurseAvailabilityWindowService {

    List<NurseAvailabilityWindowResponse> getMyWindows();

    NurseAvailabilityWindowResponse createMyWindow(CreateNurseAvailabilityWindowRequest request);

    NurseAvailabilityWindowResponse cancelMyWindow(UUID windowId);
}
