package com.minduc.happabi.service.booking;

import com.minduc.happabi.dto.request.admin.ApproveMotherRefundRequest;
import com.minduc.happabi.dto.request.admin.RejectMotherRefundRequest;
import com.minduc.happabi.dto.response.booking.MotherRefundResponse;
import com.minduc.happabi.enums.MotherRefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IMotherRefundService {
    Page<MotherRefundResponse> getMyRefundRequests(Pageable pageable);

    Page<MotherRefundResponse> getRefundRequests(MotherRefundStatus status, Pageable pageable);

    MotherRefundResponse approveRefundRequest(UUID requestId, ApproveMotherRefundRequest request, MultipartFile evidence);

    MotherRefundResponse rejectRefundRequest(UUID requestId, RejectMotherRefundRequest request);
}
