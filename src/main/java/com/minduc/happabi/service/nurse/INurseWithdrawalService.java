package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.request.admin.ApproveWithdrawalRequest;
import com.minduc.happabi.dto.request.admin.RejectWithdrawalRequest;
import com.minduc.happabi.dto.request.nurse.CreateWithdrawalRequest;
import com.minduc.happabi.dto.response.nurse.NurseWithdrawalResponse;
import com.minduc.happabi.enums.NurseWithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface INurseWithdrawalService {

    NurseWithdrawalResponse createMyWithdrawalRequest(CreateWithdrawalRequest request);

    Page<NurseWithdrawalResponse> getMyWithdrawalRequests(Pageable pageable);

    NurseWithdrawalResponse cancelMyWithdrawalRequest(UUID requestId);

    Page<NurseWithdrawalResponse> getWithdrawalRequests(NurseWithdrawalStatus status, Pageable pageable);

    NurseWithdrawalResponse approveWithdrawalRequest(UUID requestId,
                                                     ApproveWithdrawalRequest request,
                                                     MultipartFile evidence);

    NurseWithdrawalResponse rejectWithdrawalRequest(UUID requestId, RejectWithdrawalRequest request);
}
