package com.minduc.happabi.dto.response.mother;

import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.WorkSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MotherDashboardResponse {

    private DashboardMetrics metrics;
    private List<UpcomingSession> upcomingSessions;
    private List<RecommendedNurse> recommendedNurses;
    private boolean profileLocationConfigured;
    private OffsetDateTime generatedAt;

    @Getter
    @Builder
    public static class DashboardMetrics {
        private long upcomingSessions;
        private long completedSessions;
        private long paidBookings;
        private Double averageRatingGiven;
    }

    @Getter
    @Builder
    public static class UpcomingSession {
        private UUID workSessionId;
        private UUID bookingId;
        private String nurseName;
        private String nurseAvatarUrl;
        private String serviceName;
        private OffsetDateTime scheduledStartAt;
        private OffsetDateTime scheduledEndAt;
        private WorkSessionStatus status;
    }

    @Getter
    @Builder
    public static class RecommendedNurse {
        private UUID nurseProfileId;
        private String fullName;
        private String avatarUrl;
        private NurseSpecialty specialty;
        private BigDecimal ratingAvg;
        private Integer totalReviews;
        private String city;
    }
}
