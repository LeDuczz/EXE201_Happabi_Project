package com.minduc.happabi.service.worksession;

import com.minduc.happabi.dto.request.worksession.CompleteChecklistItemRequest;
import com.minduc.happabi.dto.request.worksession.ReportWorkSessionRequest;
import com.minduc.happabi.dto.response.worksession.WorkSessionResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.WorkSession;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IWorkSessionService {

    WorkSession createFromAcceptedBooking(Booking booking);

    List<WorkSessionResponse> getMyNurseWorkSessions();

    WorkSessionResponse getMyNurseWorkSession(UUID workSessionId);

    WorkSessionResponse checkIn(UUID workSessionId, List<MultipartFile> images);

    WorkSessionResponse completeChecklistItem(UUID workSessionId,
                                              UUID checklistItemId,
                                              CompleteChecklistItemRequest request,
                                              List<MultipartFile> images);

    WorkSessionResponse undoChecklistItem(UUID workSessionId, UUID checklistItemId);

    WorkSessionResponse checkout(UUID workSessionId);

    List<WorkSessionResponse> getMyMotherWorkSessions();

    WorkSessionResponse getMyMotherWorkSession(UUID workSessionId);

    WorkSessionResponse confirmByMother(UUID workSessionId);

    WorkSessionResponse reportByMother(UUID workSessionId, ReportWorkSessionRequest request);
}
