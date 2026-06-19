package com.minduc.happabi.service.worksession;

import com.minduc.happabi.dto.request.admin.ReviewWorkSessionIncidentRequest;
import com.minduc.happabi.dto.request.worksession.ReportWorkSessionIncidentRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionIncidentResponse;
import com.minduc.happabi.enums.WorkSessionIncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IWorkSessionIncidentService {
    WorkSessionIncidentResponse reportMotherUnreachable(UUID workSessionId,
                                                        ReportWorkSessionIncidentRequest request,
                                                        List<MultipartFile> images);

    Page<WorkSessionIncidentResponse> getIncidents(WorkSessionIncidentStatus status, Pageable pageable);

    WorkSessionIncidentResponse approveIncident(UUID incidentId, ReviewWorkSessionIncidentRequest request);

    WorkSessionIncidentResponse rejectIncident(UUID incidentId, ReviewWorkSessionIncidentRequest request);

    WorkSessionIncidentResponse markNurseNoShow(UUID workSessionId, ReviewWorkSessionIncidentRequest request);
}
