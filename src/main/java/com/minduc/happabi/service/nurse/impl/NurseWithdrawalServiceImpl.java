package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.dto.request.admin.ApproveWithdrawalRequest;
import com.minduc.happabi.dto.request.admin.RejectWithdrawalRequest;
import com.minduc.happabi.dto.request.nurse.CreateWithdrawalRequest;
import com.minduc.happabi.dto.response.nurse.NurseWithdrawalResponse;
import com.minduc.happabi.entity.NurseBankAccount;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.NurseWithdrawalRequest;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.NurseBankAccountStatus;
import com.minduc.happabi.enums.NurseWithdrawalStatus;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.NurseWalletErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseBankAccountRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.NurseWithdrawalRequestRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.nurse.INurseWithdrawalService;
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
public class NurseWithdrawalServiceImpl implements INurseWithdrawalService {

    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = BigDecimal.valueOf(1000);

    private final NurseWithdrawalRequestRepository withdrawalRequestRepository;
    private final NurseBankAccountRepository nurseBankAccountRepository;
    private final NurseWalletRepository nurseWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserRepository userRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final INotificationPublisher notificationPublisher;
    private final IAdminWalletLedgerService adminWalletLedgerService;
    private final IS3Service s3Service;

    @Override
    @LogExecution
    @TimedAction("CREATE_NURSE_WITHDRAWAL_REQUEST")
    @AuditAction(action = "CREATE_NURSE_WITHDRAWAL_REQUEST", resourceType = "NURSE_WITHDRAWAL")
    @Transactional
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public NurseWithdrawalResponse createMyWithdrawalRequest(CreateWithdrawalRequest request) {
        NurseProfile nurseProfile = currentNurseProfile();
        BigDecimal amount = normalizeAmount(request.getAmount());
        NurseBankAccount bankAccount = nurseBankAccountRepository
                .findByNurseProfile_IdAndStatus(nurseProfile.getId(), NurseBankAccountStatus.ACTIVE)
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.WITHDRAWAL_BANK_ACCOUNT_REQUIRED));
        NurseWallet wallet = lockWallet(nurseProfile.getId());
        normalizeWallet(wallet);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_BALANCE_INSUFFICIENT);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setLockedWithdrawalAmount(wallet.getLockedWithdrawalAmount().add(amount));
        nurseWalletRepository.save(wallet);

        NurseWithdrawalRequest withdrawal = withdrawalRequestRepository.save(NurseWithdrawalRequest.builder()
                .nurseProfile(nurseProfile)
                .amount(amount)
                .bankAccount(bankAccount)
                .bankName(bankAccount.getBankName())
                .bankAccountNumber(bankAccount.getBankAccountNumber())
                .bankAccountHolder(bankAccount.getBankAccountHolder())
                .status(NurseWithdrawalStatus.PENDING)
                .build());

        recordWalletTransaction(nurseProfile.getId(), TransactionType.PAYOUT, amount.negate(),
                amount.negate(), "Withdrawal request hold " + withdrawal.getId());
        notifyAdmins(withdrawal);
        notifyNurse(withdrawal,
                "Withdrawal request created",
                "Your withdrawal request of %s VND has been created and is waiting for admin approval."
                        .formatted(moneyText(withdrawal.getAmount())));
        log.info("[Withdrawal] Created request id={} nurseId={} amount={}",
                withdrawal.getId(), nurseProfile.getId(), amount);
        return toResponse(withdrawal);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public Page<NurseWithdrawalResponse> getMyWithdrawalRequests(Pageable pageable) {
        return withdrawalRequestRepository.findByNurseProfile_IdOrderByCreatedAtDesc(
                        currentNurseProfile().getId(), pageable)
                .map(this::toResponse);
    }

    @Override
    @LogExecution
    @TimedAction("CANCEL_NURSE_WITHDRAWAL_REQUEST")
    @AuditAction(action = "CANCEL_NURSE_WITHDRAWAL_REQUEST", resourceType = "NURSE_WITHDRAWAL")
    @Transactional
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public NurseWithdrawalResponse cancelMyWithdrawalRequest(UUID requestId) {
        NurseProfile nurseProfile = currentNurseProfile();
        NurseWithdrawalRequest withdrawal = lockWithdrawal(requestId);
        if (!withdrawal.getNurseProfile().getId().equals(nurseProfile.getId())) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND);
        }
        requirePending(withdrawal);
        releaseHeldAmount(withdrawal, "Withdrawal request cancelled " + withdrawal.getId());
        withdrawal.setStatus(NurseWithdrawalStatus.CANCELLED);
        withdrawal.setCancelledAt(OffsetDateTime.now());
        NurseWithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        notifyNurse(saved,
                "Withdrawal request cancelled",
                "Your withdrawal request has been cancelled and the held amount has been returned to your wallet.");
        notifyAdminsWithdrawalCancelled(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<NurseWithdrawalResponse> getWithdrawalRequests(NurseWithdrawalStatus status, Pageable pageable) {
        Page<NurseWithdrawalRequest> page = status == null
                ? withdrawalRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                : withdrawalRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @LogExecution
    @TimedAction("APPROVE_NURSE_WITHDRAWAL_REQUEST")
    @AuditAction(action = "APPROVE_NURSE_WITHDRAWAL_REQUEST", resourceType = "NURSE_WITHDRAWAL")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public NurseWithdrawalResponse approveWithdrawalRequest(UUID requestId,
                                                            ApproveWithdrawalRequest request,
                                                            MultipartFile evidence) {
        NurseWithdrawalRequest withdrawal = lockWithdrawal(requestId);
        requirePending(withdrawal);
        if (evidence == null || evidence.isEmpty()) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_EVIDENCE_REQUIRED);
        }
        String uploadedKey = null;
        try {
            uploadedKey = s3Service.upload("withdrawals", withdrawal.getNurseProfile().getId().toString(), evidence);
            withdrawal.setTransferEvidenceS3Key(uploadedKey);
            NurseWallet wallet = lockWallet(withdrawal.getNurseProfile().getId());
            normalizeWallet(wallet);
            if (wallet.getLockedWithdrawalAmount().compareTo(withdrawal.getAmount()) < 0) {
                throw new AppException(NurseWalletErrorCode.WITHDRAWAL_BALANCE_INSUFFICIENT,
                        "Locked withdrawal amount is not enough to approve request " + withdrawal.getId());
            }
            wallet.setLockedWithdrawalAmount(wallet.getLockedWithdrawalAmount().subtract(withdrawal.getAmount()));
            nurseWalletRepository.save(wallet);

            adminWalletLedgerService.recordWithdrawalPayout(withdrawal.getId(), withdrawal.getAmount());
            User admin = userAccountLookupService.getCurrentUser();
            withdrawal.setStatus(NurseWithdrawalStatus.APPROVED);
            withdrawal.setProcessedByAdmin(admin);
            withdrawal.setApprovedAt(OffsetDateTime.now());
            withdrawal.setAdminNote(cleanOptional(request == null ? null : request.getAdminNote()));
            withdrawal.setBankTransactionCode(cleanOptional(request == null ? null : request.getBankTransactionCode()));
            NurseWithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
            notifyNurse(saved,
                    "Withdrawal request approved",
                    "Your withdrawal request has been approved. Please check your bank account.");
            log.info("[Withdrawal] Approved request id={} amount={}", saved.getId(), saved.getAmount());
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
    @TimedAction("REJECT_NURSE_WITHDRAWAL_REQUEST")
    @AuditAction(action = "REJECT_NURSE_WITHDRAWAL_REQUEST", resourceType = "NURSE_WITHDRAWAL")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public NurseWithdrawalResponse rejectWithdrawalRequest(UUID requestId, RejectWithdrawalRequest request) {
        NurseWithdrawalRequest withdrawal = lockWithdrawal(requestId);
        requirePending(withdrawal);
        releaseHeldAmount(withdrawal, "Withdrawal request rejected " + withdrawal.getId());
        withdrawal.setStatus(NurseWithdrawalStatus.REJECTED);
        withdrawal.setProcessedByAdmin(userAccountLookupService.getCurrentUser());
        withdrawal.setRejectedAt(OffsetDateTime.now());
        withdrawal.setAdminNote(cleanRequired(request.getAdminNote()));
        NurseWithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        notifyNurse(saved,
                "Withdrawal request rejected",
                "Your withdrawal request was rejected. Reason: " + saved.getAdminNote());
        log.info("[Withdrawal] Rejected request id={} amount={}", saved.getId(), saved.getAmount());
        return toResponse(saved);
    }

    private NurseProfile currentNurseProfile() {
        return nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
    }

    private NurseWallet lockWallet(UUID nurseProfileId) {
        return nurseWalletRepository.findByNurseIdForUpdate(nurseProfileId)
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.NURSE_WALLET_NOT_FOUND));
    }

    private NurseWithdrawalRequest lockWithdrawal(UUID requestId) {
        return withdrawalRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(NurseWalletErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));
    }

    private void requirePending(NurseWithdrawalRequest withdrawal) {
        if (withdrawal.getStatus() != NurseWithdrawalStatus.PENDING) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_REQUEST_NOT_PENDING);
        }
    }

    private void releaseHeldAmount(NurseWithdrawalRequest withdrawal, String description) {
        NurseWallet wallet = lockWallet(withdrawal.getNurseProfile().getId());
        normalizeWallet(wallet);
        if (wallet.getLockedWithdrawalAmount().compareTo(withdrawal.getAmount()) < 0) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_BALANCE_INSUFFICIENT,
                    "Locked withdrawal amount is not enough to release request " + withdrawal.getId());
        }
        wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
        wallet.setLockedWithdrawalAmount(wallet.getLockedWithdrawalAmount().subtract(withdrawal.getAmount()));
        nurseWalletRepository.save(wallet);
        recordWalletTransaction(withdrawal.getNurseProfile().getId(), TransactionType.TOPUP_WALLET,
                withdrawal.getAmount(), withdrawal.getAmount(), description);
    }

    private void normalizeWallet(NurseWallet wallet) {
        if (wallet.getBalance() == null) {
            wallet.setBalance(BigDecimal.ZERO);
        }
        if (wallet.getDepositBalance() == null) {
            wallet.setDepositBalance(BigDecimal.ZERO);
        }
        if (wallet.getLockedWithdrawalAmount() == null) {
            wallet.setLockedWithdrawalAmount(BigDecimal.ZERO);
        }
        if (wallet.getLockedWithdrawalAmount().signum() < 0) {
            wallet.setLockedWithdrawalAmount(BigDecimal.ZERO);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_WITHDRAWAL_AMOUNT) < 0) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_AMOUNT_INVALID);
        }
        return amount.stripTrailingZeros();
    }

    private void recordWalletTransaction(UUID nurseProfileId,
                                         TransactionType type,
                                         BigDecimal amount,
                                         BigDecimal walletImpact,
                                         String description) {
        walletTransactionRepository.save(WalletTransaction.builder()
                .nurseId(nurseProfileId)
                .transactionType(type)
                .amount(amount)
                .walletImpact(walletImpact)
                .depositImpact(BigDecimal.ZERO)
                .status(TransactionStatus.SUCCESS)
                .referenceId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                .description(description)
                .build());
    }

    private void notifyAdmins(NurseWithdrawalRequest withdrawal) {
        userRepository.findActiveUsersByRoleName(UserRole.ADMIN).forEach(admin ->
                notificationPublisher.publish(
                        admin.getId(),
                        NotificationType.WORK_SESSION_UPDATED,
                        "New withdrawal request",
                        "%s requested a withdrawal of %s VND."
                                .formatted(withdrawal.getNurseProfile().getUser().getFullName(),
                                        moneyText(withdrawal.getAmount())),
                        "NURSE_WITHDRAWAL",
                        withdrawal.getId().toString()));
    }

    private void notifyAdminsWithdrawalCancelled(NurseWithdrawalRequest withdrawal) {
        userRepository.findActiveUsersByRoleName(UserRole.ADMIN).forEach(admin ->
                notificationPublisher.publish(
                        admin.getId(),
                        NotificationType.WORK_SESSION_UPDATED,
                        "Withdrawal request cancelled",
                        "%s cancelled a withdrawal request of %s VND."
                                .formatted(withdrawal.getNurseProfile().getUser().getFullName(),
                                        moneyText(withdrawal.getAmount())),
                        "NURSE_WITHDRAWAL",
                        withdrawal.getId().toString()));
    }

    private void notifyNurse(NurseWithdrawalRequest withdrawal,
                             String title,
                             String message) {
        notificationPublisher.publish(
                withdrawal.getNurseProfile().getUser().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "NURSE_WITHDRAWAL",
                withdrawal.getId().toString());
    }

    private String moneyText(BigDecimal amount) {
        return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
    }

    private NurseWithdrawalResponse toResponse(NurseWithdrawalRequest withdrawal) {
        return NurseWithdrawalResponse.builder()
                .id(withdrawal.getId())
                .nurseProfileId(withdrawal.getNurseProfile().getId())
                .nurseName(withdrawal.getNurseProfile().getUser().getFullName())
                .bankAccountId(withdrawal.getBankAccount() == null ? null : withdrawal.getBankAccount().getId())
                .amount(withdrawal.getAmount())
                .bankName(withdrawal.getBankName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .bankAccountHolder(withdrawal.getBankAccountHolder())
                .status(withdrawal.getStatus())
                .nurseNote(withdrawal.getNurseNote())
                .adminNote(withdrawal.getAdminNote())
                .bankTransactionCode(withdrawal.getBankTransactionCode())
                .transferEvidenceUrl(s3Service.presign(withdrawal.getTransferEvidenceS3Key()))
                .processedByAdminName(withdrawal.getProcessedByAdmin() == null
                        ? null
                        : withdrawal.getProcessedByAdmin().getFullName())
                .createdAt(withdrawal.getCreatedAt())
                .updatedAt(withdrawal.getUpdatedAt())
                .approvedAt(withdrawal.getApprovedAt())
                .rejectedAt(withdrawal.getRejectedAt())
                .cancelledAt(withdrawal.getCancelledAt())
                .build();
    }

    private String cleanRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(NurseWalletErrorCode.WITHDRAWAL_AMOUNT_INVALID);
        }
        return value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
