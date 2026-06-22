package com.minduc.happabi.dto.response.nurse;

import com.minduc.happabi.enums.WorkSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class NurseDashboardResponse {
    private long todaySessionCount;
    private int checklistCompletionPercent;
    private BigDecimal todayRevenue;
    private BigDecimal ratingAvg;
    private int totalReviews;
    private List<TodaySession> todaySessions;
    private List<String> activeChecklistPreview;

    @Getter
    @Builder
    public static class TodaySession {
        private UUID id;
        private String motherName;
        private String serviceName;
        private String serviceAddress;
        private OffsetDateTime scheduledStartAt;
        private WorkSessionStatus status;
        private long checklistCompletedCount;
        private long checklistTotalCount;
    }
}
