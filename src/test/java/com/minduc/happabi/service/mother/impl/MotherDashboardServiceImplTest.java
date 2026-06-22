package com.minduc.happabi.service.mother.impl;

import com.minduc.happabi.dto.response.mother.MotherDashboardResponse;
import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseReviewRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.mother.IMotherNurseProfileService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotherDashboardServiceImplTest {

    @Mock
    private UserAccountLookupService userAccountLookupService;

    @Mock
    private MotherProfileRepository motherProfileRepository;

    @Mock
    private WorkSessionRepository workSessionRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private NurseReviewRepository nurseReviewRepository;

    @Mock
    private IMotherNurseProfileService motherNurseProfileService;

    @InjectMocks
    private MotherDashboardServiceImpl service;

    @Test
    void returnsLiveMetricsUpcomingSessionsAndLocationMatchedRecommendations() {
        User mother = User.builder().id(UUID.randomUUID()).build();
        User nurseUser = User.builder().id(UUID.randomUUID()).fullName("Nurse One").build();
        NurseProfile nurseProfile = NurseProfile.builder().id(UUID.randomUUID()).user(nurseUser).build();
        WorkSession session = WorkSession.builder()
                .id(UUID.randomUUID())
                .booking(Booking.builder().id(UUID.randomUUID()).build())
                .nurseProfile(nurseProfile)
                .serviceOffering(ServiceOffering.builder().serviceName("Newborn care").build())
                .status(WorkSessionStatus.SCHEDULED)
                .scheduledStartAt(OffsetDateTime.now().plusDays(1))
                .scheduledEndAt(OffsetDateTime.now().plusDays(1).plusHours(1))
                .build();
        NursePublicProfileResponse recommendation = NursePublicProfileResponse.builder()
                .profileId(nurseProfile.getId())
                .fullName(nurseUser.getFullName())
                .specialty(NurseSpecialty.NURSE)
                .ratingAvg(new BigDecimal("4.80"))
                .totalReviews(10)
                .city("Ha Noi")
                .build();

        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(motherProfileRepository.findByUser(mother)).thenReturn(Optional.of(
                MotherProfile.builder().user(mother).city("Ha Noi").build()));
        when(workSessionRepository.countByMother_IdAndStatusInAndScheduledStartAtGreaterThanEqual(
                eq(mother.getId()), any(), any())).thenReturn(1L);
        when(workSessionRepository.countByMother_IdAndStatusIn(eq(mother.getId()), any())).thenReturn(2L);
        when(bookingRepository.countByMother_IdAndStatusIn(eq(mother.getId()), any())).thenReturn(3L);
        when(nurseReviewRepository.averageRatingGivenByMother(mother.getId())).thenReturn(4.5D);
        when(workSessionRepository.findUpcomingByMotherId(eq(mother.getId()), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(session));
        when(motherNurseProfileService.searchActiveNurses(eq(null), eq("Ha Noi"), eq(null), eq(null), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(recommendation)));

        MotherDashboardResponse dashboard = service.getMyDashboard();

        assertThat(dashboard.getMetrics().getUpcomingSessions()).isEqualTo(1);
        assertThat(dashboard.getMetrics().getCompletedSessions()).isEqualTo(2);
        assertThat(dashboard.getMetrics().getPaidBookings()).isEqualTo(3);
        assertThat(dashboard.getMetrics().getAverageRatingGiven()).isEqualTo(4.5D);
        assertThat(dashboard.isProfileLocationConfigured()).isTrue();
        assertThat(dashboard.getUpcomingSessions()).singleElement()
                .extracting(MotherDashboardResponse.UpcomingSession::getNurseName)
                .isEqualTo("Nurse One");
        assertThat(dashboard.getRecommendedNurses()).singleElement()
                .extracting(MotherDashboardResponse.RecommendedNurse::getCity)
                .isEqualTo("Ha Noi");
    }
}
