package com.minduc.happabi.service.mother;

import com.minduc.happabi.dto.request.mother.NurseAiComparisonRequest;
import com.minduc.happabi.dto.response.mother.NurseAiComparisonResponse;

public interface IMotherNurseComparisonService {

    NurseAiComparisonResponse compareNurses(NurseAiComparisonRequest request);
}
