package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.worksession.WorkSessionChecklistItemResponse;
import com.minduc.happabi.dto.response.worksession.WorkSessionEvidenceResponse;
import com.minduc.happabi.dto.response.worksession.WorkSessionResponse;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.entity.WorkSessionChecklistItem;
import com.minduc.happabi.entity.WorkSessionEvidence;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkSessionMapper {

    @Mapping(target = "bookingId", source = "session.booking.id")
    @Mapping(target = "nurseProfileId", source = "session.nurseProfile.id")
    @Mapping(target = "nurseName", source = "session.nurseProfile.user.fullName")
    @Mapping(target = "motherId", source = "session.mother.id")
    @Mapping(target = "motherName", source = "session.mother.fullName")
    @Mapping(target = "serviceOfferingId", source = "session.serviceOffering.id")
    @Mapping(target = "serviceName", source = "session.serviceOffering.serviceName")
    @Mapping(target = "checkInEvidences", source = "checkInEvidences")
    @Mapping(target = "checklistItems", source = "checklistItems")
    WorkSessionResponse toResponse(WorkSession session,
                                   List<WorkSessionEvidenceResponse> checkInEvidences,
                                   List<WorkSessionChecklistItemResponse> checklistItems);

    @Mapping(target = "evidences", source = "evidences")
    WorkSessionChecklistItemResponse toChecklistItemResponse(WorkSessionChecklistItem item,
                                                             List<WorkSessionEvidenceResponse> evidences);

    @Mapping(target = "previewUrl", source = "previewUrl")
    WorkSessionEvidenceResponse toEvidenceResponse(WorkSessionEvidence evidence, String previewUrl);
}
