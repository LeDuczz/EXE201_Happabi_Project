package com.minduc.happabi.service.nurse.impl;

import com.minduc.happabi.dto.response.nurse.NurseDashboardResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.entity.WorkSessionChecklistItem;
import com.minduc.happabi.enums.TransactionStatus;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.enums.WorkSessionChecklistStatus;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.repository.WorkSessionChecklistItemRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.nurse.INurseDashboardService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NurseDashboardServiceImpl implements INurseDashboardService {
    private final UserAccountLookupService userAccountLookupService;
    private final NurseProfileRepository nurseProfileRepository;
    private final WorkSessionRepository workSessionRepository;
    private final WorkSessionChecklistItemRepository checklistItemRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('NURSE')")
    @LogExecution
    @TimedAction("GET_NURSE_DASHBOARD")
    @AuditAction(action = "READ_NURSE_DASHBOARD", resourceType = "NURSE_DASHBOARD")
    public NurseDashboardResponse getMyDashboard() {
        NurseProfile profile = nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        List<WorkSession> sessions = workSessionRepository.findDashboardSessionsForNurse(
                profile.getId(), dayStart, dayEnd, WorkSessionStatus.CANCELLED);
        Map<UUID, List<WorkSessionChecklistItem>> checklistBySession = checklistItemsBySession(sessions);
        long checklistTotal = checklistBySession.values().stream().mapToLong(List::size).sum();
        long checklistCompleted = checklistBySession.values().stream()
                .flatMap(List::stream)
                .filter(item -> item.getStatus() == WorkSessionChecklistStatus.COMPLETED)
                .count();

        return NurseDashboardResponse.builder()
                .todaySessionCount(sessions.size())
                .checklistCompletionPercent(checklistTotal == 0 ? 0 : (int) Math.round(checklistCompleted * 100.0 / checklistTotal))
                .todayRevenue(walletTransactionRepository.sumWalletImpactByNurseIdAndTransactionTypeAndStatusBetween(
                        profile.getId(), TransactionType.BOOKING_EARNING, TransactionStatus.SUCCESS,
                        dayStart.toInstant(), dayEnd.toInstant()))
                .ratingAvg(profile.getRatingAvg())
                .totalReviews(profile.getTotalReviews())
                .todaySessions(sessions.stream().map(session -> toSession(session, checklistBySession.getOrDefault(session.getId(), List.of()))).toList())
                .activeChecklistPreview(activeChecklistPreview(sessions, checklistBySession))
                .build();
    }

    private Map<UUID, List<WorkSessionChecklistItem>> checklistItemsBySession(List<WorkSession> sessions) {
        if (sessions.isEmpty()) return Map.of();
        return checklistItemRepository.findByWorkSession_IdInOrderByWorkSession_IdAscSortOrderAsc(
                        sessions.stream().map(WorkSession::getId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getWorkSession().getId()));
    }

    private NurseDashboardResponse.TodaySession toSession(WorkSession session, List<WorkSessionChecklistItem> checklistItems) {
        long completed = checklistItems.stream().filter(item -> item.getStatus() == WorkSessionChecklistStatus.COMPLETED).count();
        return NurseDashboardResponse.TodaySession.builder()
                .id(session.getId()).motherName(session.getMother().getFullName())
                .serviceName(session.getServiceOffering().getServiceName())
                .serviceAddress(session.getBooking().getServiceAddress())
                .scheduledStartAt(session.getScheduledStartAt()).status(session.getStatus())
                .checklistCompletedCount(completed).checklistTotalCount(checklistItems.size()).build();
    }

    private List<String> activeChecklistPreview(List<WorkSession> sessions, Map<UUID, List<WorkSessionChecklistItem>> checklistBySession) {
        return sessions.stream().filter(session -> session.getStatus() == WorkSessionStatus.IN_PROGRESS).findFirst()
                .map(session -> checklistBySession.getOrDefault(session.getId(), List.of()).stream()
                        .filter(item -> item.getStatus() == WorkSessionChecklistStatus.PENDING)
                        .map(WorkSessionChecklistItem::getTitle).limit(3).toList())
                .orElse(List.of());
    }
}
