package com.minduc.happabi.dto.response.admin.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminOperationsDashboardResponse {

    private final ActionQueue actionQueue;
    private final BookingOperations bookingOperations;
    private final FinancialControl financialControl;
    private final NurseSupplyHealth nurseSupplyHealth;
    private final FeedbackInsight feedbackInsight;
    private final List<RiskAlert> riskAlerts;
    private final List<DailyMetric> appPaymentTrend;
    private final OffsetDateTime generatedAt;

    @Getter
    @Builder
    public static class ActionQueue {
        private final long pendingNurseProfiles;
        private final long pendingWithdrawals;
        private final long pendingRefunds;
        private final long pendingIncidents;
        private final long newFeedbacks;
        private final long pendingKnowledgeItems;
        private final long pendingPayments;
        private final long waitingMotherConfirmations;
    }

    @Getter
    @Builder
    public static class BookingOperations {
        private final long todayBookings;
        private final long upcoming24hBookings;
        private final long pendingPaymentBookings;
        private final long paidBookingsToday;
        private final long cancelledBookingsToday;
        private final long activeWorkSessions;
        private final long waitingCheckInSessions;
        private final long inProgressSessions;
        private final long waitingMotherConfirmationSessions;
        private final long reportedSessions;
    }

    @Getter
    @Builder
    public static class FinancialControl {
        private final BigDecimal adminWalletBalance;
        private final BigDecimal todayAppPayments;
        private final BigDecimal last7DaysAppPayments;
        private final BigDecimal last30DaysAppPayments;
        private final BigDecimal todayPlatformRevenue;
        private final BigDecimal last30DaysPlatformRevenue;
        private final BigDecimal pendingWithdrawalAmount;
        private final BigDecimal pendingRefundAmount;
        private final long pendingWithdrawals;
        private final long pendingRefunds;
    }

    @Getter
    @Builder
    public static class NurseSupplyHealth {
        private final long totalNurses;
        private final long activeNurses;
        private final long availableNurses;
        private final long busyNurses;
        private final long offlineNurses;
        private final long suspendedNurses;
        private final long pendingReviewNurses;
        private final long pendingContractNurses;
        private final long penalizedNurses;
    }

    @Getter
    @Builder
    public static class FeedbackInsight {
        private final long newFeedbacks;
        private final long reviewingFeedbacks;
        private final long plannedFeedbacks;
        private final long resolvedFeedbacks;
        private final BigDecimal averageRating;
        private final List<LatestFeedback> latestFeedbacks;
    }

    @Getter
    @Builder
    public static class LatestFeedback {
        private final UUID id;
        private final String title;
        private final String category;
        private final String status;
        private final String submittedByName;
        private final String submittedByRole;
        private final Integer rating;
        private final OffsetDateTime createdAt;
    }

    @Getter
    @Builder
    public static class RiskAlert {
        private final String severity;
        private final String title;
        private final String message;
        private final String targetPath;
        private final long count;
    }

    @Getter
    @Builder
    public static class DailyMetric {
        private final LocalDate date;
        private final BigDecimal value;
    }
}
