package com.minduc.happabi.service.mother.impl;

import com.minduc.happabi.dto.response.mother.MotherDashboardResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseReviewRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.mother.IMotherDashboardService;
import com.minduc.happabi.service.mother.IMotherNurseProfileService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MotherDashboardServiceImpl implements IMotherDashboardService {

    private static final int PREVIEW_LIMIT = 3;
    private static final Set<WorkSessionStatus> UPCOMING_STATUSES = Set.of(WorkSessionStatus.SCHEDULED);
    private static final Set<WorkSessionStatus> COMPLETED_STATUSES = Set.of(
            WorkSessionStatus.COMPLETED,
            WorkSessionStatus.AUTO_CONFIRMED
    );
    private static final Set<BookingStatus> PAID_BOOKING_STATUSES = Set.of(
            BookingStatus.PENDING_NURSE_ACCEPTANCE,
            BookingStatus.ACCEPTED,
            BookingStatus.COMPLETED
    );

    private final UserAccountLookupService userAccountLookupService;
    private final MotherProfileRepository motherProfileRepository;
    private final WorkSessionRepository workSessionRepository;
    private final BookingRepository bookingRepository;
    private final NurseReviewRepository nurseReviewRepository;
    private final IMotherNurseProfileService motherNurseProfileService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    @LogExecution
    @TimedAction("GET_MOTHER_DASHBOARD")
    @AuditAction(action = "READ_MOTHER_DASHBOARD", resourceType = "MOTHER_DASHBOARD")
    public MotherDashboardResponse getMyDashboard() {
        UUID motherId = userAccountLookupService.getCurrentUser().getId();
        OffsetDateTime now = OffsetDateTime.now();
        MotherProfile motherProfile = motherProfileRepository
                .findByUser(userAccountLookupService.getCurrentUser())
                .orElse(null);
        String city = motherProfile == null ? null : normalize(motherProfile.getCity());

        MotherDashboardResponse.DashboardMetrics metrics = MotherDashboardResponse.DashboardMetrics.builder()
                .upcomingSessions(workSessionRepository.countByMother_IdAndStatusInAndScheduledStartAtGreaterThanEqual(
                        motherId, UPCOMING_STATUSES, now))
                .completedSessions(workSessionRepository.countByMother_IdAndStatusIn(motherId, COMPLETED_STATUSES))
                .paidBookings(bookingRepository.countByMother_IdAndStatusIn(motherId, PAID_BOOKING_STATUSES))
                .averageRatingGiven(nurseReviewRepository.averageRatingGivenByMother(motherId))
                .build();

        List<MotherDashboardResponse.UpcomingSession> upcomingSessions = workSessionRepository
                .findUpcomingByMotherId(motherId, UPCOMING_STATUSES, now, PageRequest.of(0, PREVIEW_LIMIT))
                .stream()
                .map(this::toUpcomingSession)
                .toList();

        List<MotherDashboardResponse.RecommendedNurse> recommendedNurses = motherNurseProfileService
                .searchActiveNurses(null, city, null, null, true,
                        PageRequest.of(0, PREVIEW_LIMIT, Sort.by(Sort.Direction.DESC, "ratingAvg")))
                .getContent()
                .stream()
                .map(this::toRecommendedNurse)
                .toList();

        return MotherDashboardResponse.builder()
                .metrics(metrics)
                .upcomingSessions(upcomingSessions)
                .recommendedNurses(recommendedNurses)
                .profileLocationConfigured(city != null)
                .generatedAt(now)
                .build();
    }

    private MotherDashboardResponse.UpcomingSession toUpcomingSession(WorkSession session) {
        return MotherDashboardResponse.UpcomingSession.builder()
                .workSessionId(session.getId())
                .bookingId(session.getBooking().getId())
                .nurseName(session.getNurseProfile().getUser().getFullName())
                .serviceName(session.getServiceOffering().getServiceName())
                .scheduledStartAt(session.getScheduledStartAt())
                .scheduledEndAt(session.getScheduledEndAt())
                .status(session.getStatus())
                .build();
    }

    private MotherDashboardResponse.RecommendedNurse toRecommendedNurse(NursePublicProfileResponse nurse) {
        return MotherDashboardResponse.RecommendedNurse.builder()
                .nurseProfileId(nurse.getProfileId())
                .fullName(nurse.getFullName())
                .avatarUrl(nurse.getAvatarUrl())
                .specialty(nurse.getSpecialty())
                .ratingAvg(nurse.getRatingAvg())
                .totalReviews(nurse.getTotalReviews())
                .city(nurse.getCity())
                .build();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
