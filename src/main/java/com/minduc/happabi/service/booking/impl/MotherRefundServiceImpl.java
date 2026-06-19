package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.dto.request.admin.ApproveMotherRefundRequest;
import com.minduc.happabi.dto.request.admin.RejectMotherRefundRequest;
import com.minduc.happabi.dto.response.booking.MotherRefundResponse;
import com.minduc.happabi.entity.MotherRefundRequest;
import com.minduc.happabi.enums.MotherRefundStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.MotherRefundRequestRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import com.minduc.happabi.service.booking.IMotherRefundService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotherRefundServiceImpl implements IMotherRefundService {

    private final MotherRefundRequestRepository refundRequestRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final IAdminWalletLedgerService adminWalletLedgerService;
    private final INotificationPublisher notificationPublisher;
    private final IS3Service s3Service;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    public Page<MotherRefundResponse> getMyRefundRequests(Pageable pageable) {
        return refundRequestRepository.findByMother_IdOrderByCreatedAtDesc(
                        userAccountLookupService.getCurrentUser().getId(), pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<MotherRefundResponse> getRefundRequests(MotherRefundStatus status, Pageable pageable) {
        Page<MotherRefundRequest> page = status == null
                ? refundRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                : refundRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @LogExecution
    @TimedAction("APPROVE_MOTHER_REFUND_REQUEST")
    @AuditAction(action = "APPROVE_MOTHER_REFUND_REQUEST", resourceType = "MOTHER_REFUND")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MotherRefundResponse approveRefundRequest(UUID requestId,
                                                     ApproveMotherRefundRequest request,
                                                     MultipartFile evidence) {
        MotherRefundRequest refund = lockRefund(requestId);
        requirePending(refund);
        if (evidence == null || evidence.isEmpty()) {
            throw new AppException(BookingErrorCode.MOTHER_REFUND_EVIDENCE_REQUIRED);
        }

        String uploadedKey = null;
        try {
            uploadedKey = s3Service.upload("withdrawals", refund.getMother().getId().toString(), evidence);
            refund.setTransferEvidenceS3Key(uploadedKey);
            refund.setProcessedByAdmin(userAccountLookupService.getCurrentUser());
            refund.setStatus(MotherRefundStatus.APPROVED);
            refund.setApprovedAt(OffsetDateTime.now());
            refund.setAdminNote(cleanOptional(request == null ? null : request.getAdminNote()));
            refund.setBankTransactionCode(cleanOptional(request == null ? null : request.getBankTransactionCode()));
            adminWalletLedgerService.recordBookingRefund(refund.getBooking().getId(), BigDecimal.valueOf(refund.getAmount()));
            MotherRefundRequest saved = refundRequestRepository.save(refund);
            notifyMother(saved,
                    "Refund request approved",
                    "Your refund request has been approved. Please check your bank account.");
            log.info("[MotherRefund] Approved request id={} amount={}", saved.getId(), saved.getAmount());
            return toResponse(saved);
        } catch (RuntimeException e) {
            if (uploadedKey != null) {
                s3Service.delete(uploadedKey);
            }
            throw e;
        }
    }

    @Override
    @LogExecution
    @TimedAction("REJECT_MOTHER_REFUND_REQUEST")
    @AuditAction(action = "REJECT_MOTHER_REFUND_REQUEST", resourceType = "MOTHER_REFUND")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MotherRefundResponse rejectRefundRequest(UUID requestId, RejectMotherRefundRequest request) {
        MotherRefundRequest refund = lockRefund(requestId);
        requirePending(refund);
        refund.setProcessedByAdmin(userAccountLookupService.getCurrentUser());
        refund.setStatus(MotherRefundStatus.REJECTED);
        refund.setRejectedAt(OffsetDateTime.now());
        refund.setAdminNote(cleanRequired(request == null ? null : request.getAdminNote()));
        MotherRefundRequest saved = refundRequestRepository.save(refund);
        notifyMother(saved,
                "Refund request rejected",
                "Your refund request was rejected. Reason: " + saved.getAdminNote());
        return toResponse(saved);
    }

    private MotherRefundRequest lockRefund(UUID requestId) {
        return refundRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(BookingErrorCode.MOTHER_REFUND_REQUEST_NOT_FOUND));
    }

    private void requirePending(MotherRefundRequest refund) {
        if (refund.getStatus() != MotherRefundStatus.PENDING) {
            throw new AppException(BookingErrorCode.MOTHER_REFUND_REQUEST_NOT_PENDING);
        }
    }

    private void notifyMother(MotherRefundRequest refund, String title, String message) {
        notificationPublisher.publish(
                refund.getMother().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "MOTHER_REFUND",
                refund.getId().toString());
    }

    private MotherRefundResponse toResponse(MotherRefundRequest refund) {
        return MotherRefundResponse.builder()
                .id(refund.getId())
                .bookingId(refund.getBooking().getId())
                .motherId(refund.getMother().getId())
                .motherName(refund.getMother().getFullName())
                .motherPhone(refund.getMother().getPhone())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .reason(refund.getReason())
                .adminNote(refund.getAdminNote())
                .bankTransactionCode(refund.getBankTransactionCode())
                .transferEvidenceUrl(s3Service.presign(refund.getTransferEvidenceS3Key()))
                .processedByAdminName(refund.getProcessedByAdmin() == null
                        ? null
                        : refund.getProcessedByAdmin().getFullName())
                .createdAt(refund.getCreatedAt())
                .approvedAt(refund.getApprovedAt())
                .rejectedAt(refund.getRejectedAt())
                .build();
    }

    private String cleanRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(BookingErrorCode.MOTHER_REFUND_REQUEST_NOT_PENDING, "Admin note is required.");
        }
        return value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
