package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.ActionQueue;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.BookingOperations;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.DailyMetric;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.FeedbackInsight;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.FinancialControl;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.LatestFeedback;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.NurseSupplyHealth;
import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse.RiskAlert;
import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.AdminWalletTransaction;
import com.minduc.happabi.entity.UserFeedback;
import com.minduc.happabi.enums.AdminWalletTransactionType;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.KnowledgeStatus;
import com.minduc.happabi.enums.MotherRefundStatus;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.enums.NurseWithdrawalStatus;
import com.minduc.happabi.enums.UserFeedbackStatus;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.enums.WorkSessionIncidentStatus;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.AdminWalletRepository;
import com.minduc.happabi.repository.AdminWalletTransactionRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.KnowledgeItemRepository;
import com.minduc.happabi.repository.MotherRefundRequestRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.NurseWithdrawalRequestRepository;
import com.minduc.happabi.repository.PlatformRevenueRepository;
import com.minduc.happabi.repository.UserFeedbackRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.WorkSessionIncidentRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.admin.IAdminOperationsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminOperationsDashboardServiceImpl implements IAdminOperationsDashboardService {

    private static final List<WorkSessionStatus> ACTIVE_WORK_SESSION_STATUSES = List.of(
            WorkSessionStatus.SCHEDULED,
            WorkSessionStatus.IN_PROGRESS,
            WorkSessionStatus.PENDING_MOTHER_CONFIRMATION,
            WorkSessionStatus.REPORTED
    );

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final WorkSessionRepository workSessionRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final AdminWalletRepository adminWalletRepository;
    private final AdminWalletTransactionRepository adminWalletTransactionRepository;
    private final PlatformRevenueRepository platformRevenueRepository;
    private final NurseWithdrawalRequestRepository nurseWithdrawalRequestRepository;
    private final MotherRefundRequestRepository motherRefundRequestRepository;
    private final WorkSessionIncidentRepository workSessionIncidentRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;

    @Override
    @Transactional(readOnly = true)
    @LogExecution
    @TimedAction("ADMIN_OPERATIONS_DASHBOARD_OVERVIEW")
    @AuditAction(action = "READ_ADMIN_OPERATIONS_DASHBOARD", resourceType = "ADMIN_DASHBOARD")
    public AdminOperationsDashboardResponse getOverview() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime todayStart = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime tomorrowStart = todayStart.plusDays(1);
        OffsetDateTime next24Hours = now.plusHours(24);
        OffsetDateTime last7Days = now.minusDays(7);
        OffsetDateTime last30Days = now.minusDays(30);

        long pendingWithdrawals = nurseWithdrawalRequestRepository.countByStatus(NurseWithdrawalStatus.PENDING);
        long pendingRefunds = motherRefundRequestRepository.countByStatus(MotherRefundStatus.PENDING);
        long pendingIncidents = workSessionIncidentRepository.countByStatus(WorkSessionIncidentStatus.PENDING_REVIEW);
        long newFeedbacks = userFeedbackRepository.countByStatus(UserFeedbackStatus.NEW);
        long pendingKnowledgeItems = knowledgeItemRepository.countByStatus(KnowledgeStatus.PENDING_REVIEW);
        long pendingPayments = bookingRepository.countByStatus(BookingStatus.PENDING_PAYMENT);
        long waitingMotherConfirmations = workSessionRepository.countByStatus(WorkSessionStatus.PENDING_MOTHER_CONFIRMATION);
        long pendingNurseProfiles = nurseProfileRepository.countByNurseStatus(NurseStatus.PENDING_REVIEW);

        ActionQueue actionQueue = ActionQueue.builder()
                .pendingNurseProfiles(pendingNurseProfiles)
                .pendingWithdrawals(pendingWithdrawals)
                .pendingRefunds(pendingRefunds)
                .pendingIncidents(pendingIncidents)
                .newFeedbacks(newFeedbacks)
                .pendingKnowledgeItems(pendingKnowledgeItems)
                .pendingPayments(pendingPayments)
                .waitingMotherConfirmations(waitingMotherConfirmations)
                .build();

        BookingOperations bookingOperations = buildBookingOperations(
                todayStart,
                tomorrowStart,
                now,
                next24Hours,
                pendingPayments,
                waitingMotherConfirmations
        );

        FinancialControl financialControl = buildFinancialControl(
                todayStart,
                tomorrowStart,
                last7Days,
                last30Days,
                now,
                pendingWithdrawals,
                pendingRefunds
        );

        NurseSupplyHealth nurseSupplyHealth = NurseSupplyHealth.builder()
                .totalNurses(userRepository.countByRoleName(UserRole.NURSE))
                .activeNurses(nurseProfileRepository.countByNurseStatus(NurseStatus.ACTIVE))
                .availableNurses(nurseProfileRepository.countByAvailabilityStatus(AvailabilityStatus.AVAILABLE))
                .busyNurses(nurseProfileRepository.countByAvailabilityStatus(AvailabilityStatus.BUSY))
                .offlineNurses(nurseProfileRepository.countByAvailabilityStatus(AvailabilityStatus.OFFLINE))
                .suspendedNurses(nurseProfileRepository.countByNurseStatus(NurseStatus.SUSPENDED))
                .pendingReviewNurses(pendingNurseProfiles)
                .pendingContractNurses(nurseProfileRepository.countByNurseStatus(NurseStatus.APPROVED_PENDING_CONTRACT))
                .pendingDepositNurses(nurseProfileRepository.countByNurseStatus(NurseStatus.PENDING_DEPOSIT))
                .penalizedNurses(nurseProfileRepository.countPenalizedProfiles(now))
                .build();

        FeedbackInsight feedbackInsight = FeedbackInsight.builder()
                .newFeedbacks(newFeedbacks)
                .reviewingFeedbacks(userFeedbackRepository.countByStatus(UserFeedbackStatus.REVIEWING))
                .plannedFeedbacks(userFeedbackRepository.countByStatus(UserFeedbackStatus.PLANNED))
                .resolvedFeedbacks(userFeedbackRepository.countByStatus(UserFeedbackStatus.RESOLVED))
                .averageRating(toRating(userFeedbackRepository.averageRating()))
                .latestFeedbacks(userFeedbackRepository.findTop5ByOrderByCreatedAtDesc().stream()
                        .map(this::toLatestFeedback)
                        .toList())
                .build();

        return AdminOperationsDashboardResponse.builder()
                .actionQueue(actionQueue)
                .bookingOperations(bookingOperations)
                .financialControl(financialControl)
                .nurseSupplyHealth(nurseSupplyHealth)
                .feedbackInsight(feedbackInsight)
                .riskAlerts(buildRiskAlerts(actionQueue, bookingOperations, nurseSupplyHealth))
                .appPaymentTrend(buildAppPaymentTrend(last30Days))
                .generatedAt(now)
                .build();
    }

    private BookingOperations buildBookingOperations(OffsetDateTime todayStart,
                                                     OffsetDateTime tomorrowStart,
                                                     OffsetDateTime now,
                                                     OffsetDateTime next24Hours,
                                                     long pendingPayments,
                                                     long waitingMotherConfirmations) {
        long paidBookingsToday = EnumSet.of(
                        BookingStatus.PENDING_NURSE_ACCEPTANCE,
                        BookingStatus.ACCEPTED,
                        BookingStatus.COMPLETED
                ).stream()
                .mapToLong(status -> bookingRepository.countByStatusAndCreatedAtBetween(status, todayStart, tomorrowStart))
                .sum();

        return BookingOperations.builder()
                .todayBookings(bookingRepository.countByStartAtBetween(todayStart, tomorrowStart))
                .upcoming24hBookings(bookingRepository.countByStartAtBetween(now, next24Hours))
                .pendingPaymentBookings(pendingPayments)
                .paidBookingsToday(paidBookingsToday)
                .cancelledBookingsToday(bookingRepository.countByStatusAndUpdatedAtBetween(
                        BookingStatus.CANCELLED,
                        todayStart,
                        tomorrowStart
                ))
                .activeWorkSessions(workSessionRepository.countByStatusIn(ACTIVE_WORK_SESSION_STATUSES))
                .waitingCheckInSessions(workSessionRepository.countByStatus(WorkSessionStatus.SCHEDULED))
                .inProgressSessions(workSessionRepository.countByStatus(WorkSessionStatus.IN_PROGRESS))
                .waitingMotherConfirmationSessions(waitingMotherConfirmations)
                .reportedSessions(workSessionRepository.countByStatus(WorkSessionStatus.REPORTED))
                .build();
    }

    private FinancialControl buildFinancialControl(OffsetDateTime todayStart,
                                                   OffsetDateTime tomorrowStart,
                                                   OffsetDateTime last7Days,
                                                   OffsetDateTime last30Days,
                                                   OffsetDateTime now,
                                                   long pendingWithdrawals,
                                                   long pendingRefunds) {
        Instant todayStartInstant = todayStart.toInstant();
        Instant tomorrowStartInstant = tomorrowStart.toInstant();
        Instant last7DaysInstant = last7Days.toInstant();
        Instant last30DaysInstant = last30Days.toInstant();
        Instant nowInstant = now.toInstant();

        BigDecimal pendingRefundAmount = BigDecimal.valueOf(
                motherRefundRequestRepository.sumAmountByStatus(MotherRefundStatus.PENDING)
        );

        return FinancialControl.builder()
                .adminWalletBalance(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                        .map(AdminWallet::getBalance)
                        .orElse(BigDecimal.ZERO))
                .todayAppPayments(sumAdminWalletAmount(todayStartInstant, tomorrowStartInstant))
                .last7DaysAppPayments(sumAdminWalletAmount(last7DaysInstant, nowInstant))
                .last30DaysAppPayments(sumAdminWalletAmount(last30DaysInstant, nowInstant))
                .todayPlatformRevenue(platformRevenueRepository.sumAmountByCreatedAtBetween(
                        todayStartInstant,
                        tomorrowStartInstant
                ))
                .last30DaysPlatformRevenue(platformRevenueRepository.sumAmountByCreatedAtBetween(
                        last30DaysInstant,
                        nowInstant
                ))
                .pendingWithdrawalAmount(nurseWithdrawalRequestRepository.sumAmountByStatus(NurseWithdrawalStatus.PENDING))
                .pendingRefundAmount(pendingRefundAmount)
                .pendingWithdrawals(pendingWithdrawals)
                .pendingRefunds(pendingRefunds)
                .build();
    }

    private BigDecimal sumAdminWalletAmount(Instant startAt, Instant endAt) {
        return adminWalletTransactionRepository.sumAmountByWalletAndTypeAndCreatedAtBetween(
                AdminWallet.PLATFORM_ADMIN_WALLET_ID,
                AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED,
                startAt,
                endAt
        );
    }

    private List<RiskAlert> buildRiskAlerts(ActionQueue actionQueue,
                                            BookingOperations bookingOperations,
                                            NurseSupplyHealth nurseSupplyHealth) {
        List<RiskAlert> alerts = new ArrayList<>();
        addAlert(alerts, actionQueue.getPendingIncidents(), "HIGH", "Work session incidents need review",
                "There are incident reports waiting for admin review.", "/admin/incidents");
        addAlert(alerts, actionQueue.getPendingRefunds(), "HIGH", "Refund requests need payout",
                "Mother refund requests are waiting for manual transfer evidence.", "/admin/wallet");
        addAlert(alerts, actionQueue.getPendingWithdrawals(), "MEDIUM", "Nurse withdrawals need payout",
                "Nurse withdrawal requests are waiting for admin bank transfer.", "/admin/wallet");
        addAlert(alerts, actionQueue.getPendingNurseProfiles(), "MEDIUM", "Nurse profiles need review",
                "Nurse onboarding profiles are waiting for verification.", "/admin/users");
        addAlert(alerts, actionQueue.getNewFeedbacks(), "LOW", "New user feedback",
                "Fresh product feedback is waiting for triage.", "/admin/feedbacks");
        addAlert(alerts, actionQueue.getPendingKnowledgeItems(), "LOW", "Knowledge items need review",
                "AI knowledge items are waiting for admin approval before indexing.", "/admin/knowledge");
        addAlert(alerts, bookingOperations.getWaitingMotherConfirmationSessions(), "LOW", "Sessions waiting for mother confirmation",
                "Completed sessions are waiting for mother confirmation or auto-confirm.", "/admin/incidents");
        addAlert(alerts, nurseSupplyHealth.getPenalizedNurses(), "LOW", "Nurse penalty watchlist",
                "Some nurses currently have violations or booking suspension history.", "/admin/users");
        return alerts;
    }

    private void addAlert(List<RiskAlert> alerts,
                          long count,
                          String severity,
                          String title,
                          String message,
                          String targetPath) {
        if (count <= 0) {
            return;
        }
        alerts.add(RiskAlert.builder()
                .severity(severity)
                .title(title)
                .message(message)
                .targetPath(targetPath)
                .count(count)
                .build());
    }

    private List<DailyMetric> buildAppPaymentTrend(OffsetDateTime from) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate startDate = from.toLocalDate();
        LocalDate today = LocalDate.now(zoneId);
        Map<LocalDate, BigDecimal> dailyAmounts = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            dailyAmounts.put(date, BigDecimal.ZERO);
        }

        List<AdminWalletTransaction> transactions =
                adminWalletTransactionRepository.findByWalletIdAndTransactionTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        AdminWallet.PLATFORM_ADMIN_WALLET_ID,
                        AdminWalletTransactionType.BOOKING_PAYMENT_RECEIVED,
                        from.toInstant()
                );
        for (AdminWalletTransaction transaction : transactions) {
            LocalDate date = transaction.getCreatedAt().atZone(zoneId).toLocalDate();
            dailyAmounts.computeIfPresent(date, (key, current) -> current.add(transaction.getAmount()));
        }

        return dailyAmounts.entrySet().stream()
                .map(entry -> DailyMetric.builder()
                        .date(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    private LatestFeedback toLatestFeedback(UserFeedback feedback) {
        return LatestFeedback.builder()
                .id(feedback.getId())
                .title(feedback.getTitle())
                .category(feedback.getCategory().name())
                .status(feedback.getStatus().name())
                .submittedByName(feedback.getSubmittedBy().getFullName())
                .submittedByRole(feedback.getSubmittedByRole().name())
                .rating(feedback.getRating())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    private BigDecimal toRating(Double rating) {
        if (rating == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP);
    }
}
