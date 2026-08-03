package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.Booking;
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
import com.minduc.happabi.service.admin.AdminDashboardCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminOperationsDashboardServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private WorkSessionRepository workSessionRepository;
    @Mock private NurseProfileRepository nurseProfileRepository;
    @Mock private AdminWalletRepository adminWalletRepository;
    @Mock private AdminWalletTransactionRepository adminWalletTransactionRepository;
    @Mock private PlatformRevenueRepository platformRevenueRepository;
    @Mock private NurseWithdrawalRequestRepository nurseWithdrawalRequestRepository;
    @Mock private MotherRefundRequestRepository motherRefundRequestRepository;
    @Mock private WorkSessionIncidentRepository workSessionIncidentRepository;
    @Mock private UserFeedbackRepository userFeedbackRepository;
    @Mock private KnowledgeItemRepository knowledgeItemRepository;

    private Clock clock;
    private AdminOperationsDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-04T03:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        AdminDashboardCache cache = new AdminDashboardCache(clock, 120);
        service = new AdminOperationsDashboardServiceImpl(
                userRepository,
                bookingRepository,
                workSessionRepository,
                nurseProfileRepository,
                adminWalletRepository,
                adminWalletTransactionRepository,
                platformRevenueRepository,
                nurseWithdrawalRequestRepository,
                motherRefundRequestRepository,
                workSessionIncidentRepository,
                userFeedbackRepository,
                knowledgeItemRepository,
                cache,
                clock
        );

        when(nurseWithdrawalRequestRepository.countByStatus(NurseWithdrawalStatus.PENDING)).thenReturn(1L);
        when(motherRefundRequestRepository.countByStatus(MotherRefundStatus.PENDING)).thenReturn(2L);
        when(workSessionIncidentRepository.countByStatus(WorkSessionIncidentStatus.PENDING_REVIEW)).thenReturn(3L);
        when(userFeedbackRepository.countByStatus(UserFeedbackStatus.NEW)).thenReturn(4L);
        when(userFeedbackRepository.countByStatus(UserFeedbackStatus.REVIEWING)).thenReturn(1L);
        when(userFeedbackRepository.countByStatus(UserFeedbackStatus.PLANNED)).thenReturn(1L);
        when(userFeedbackRepository.countByStatus(UserFeedbackStatus.RESOLVED)).thenReturn(1L);
        when(knowledgeItemRepository.countByStatus(KnowledgeStatus.PENDING_REVIEW)).thenReturn(5L);
        when(bookingRepository.countByStatus(BookingStatus.PENDING_PAYMENT)).thenReturn(6L);
        when(workSessionRepository.countByStatus(WorkSessionStatus.PENDING_MOTHER_CONFIRMATION)).thenReturn(7L);
        when(nurseProfileRepository.countByNurseStatus(NurseStatus.PENDING_REVIEW)).thenReturn(8L);
        when(nurseProfileRepository.countByNurseStatus(any(NurseStatus.class))).thenReturn(0L);
        when(nurseProfileRepository.countByAvailabilityStatus(any(AvailabilityStatus.class))).thenReturn(0L);
        when(nurseProfileRepository.countPenalizedProfiles(any(OffsetDateTime.class))).thenReturn(0L);
        when(userRepository.countByRoleName(UserRole.NURSE)).thenReturn(10L);
        when(bookingRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(bookingRepository.countByStatusAndUpdatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(bookingRepository.countByStartAtBetween(any(), any())).thenReturn(0L);
        when(workSessionRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(workSessionRepository.countByStatus(any(WorkSessionStatus.class))).thenReturn(0L);
        when(adminWalletRepository.findById(AdminWallet.PLATFORM_ADMIN_WALLET_ID))
                .thenReturn(Optional.of(AdminWallet.builder()
                        .id(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                        .balance(BigDecimal.valueOf(900_000))
                        .build()));
        when(motherRefundRequestRepository.sumAmountByStatus(MotherRefundStatus.PENDING)).thenReturn(30_000L);
        when(nurseWithdrawalRequestRepository.sumAmountByStatus(NurseWithdrawalStatus.PENDING)).thenReturn(BigDecimal.valueOf(40_000));
        when(platformRevenueRepository.sumAmountByCreatedAtBetween(any(), any())).thenReturn(BigDecimal.valueOf(150_000));
        when(adminWalletTransactionRepository.sumAmountByWalletAndTypeAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(10_000));
        when(bookingRepository.sumGrossAmountByStatusInAndCreatedAtBetween(anyCollection(), any(), any())).thenReturn(500_000L);
        when(userFeedbackRepository.averageRating()).thenReturn(4.75D);
        when(userFeedbackRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
    }

    @Test
    void appliesCustomRangeToFinancialPeriodAndGmvTrend() {
        OffsetDateTime firstDay = OffsetDateTime.of(2026, 7, 20, 9, 0, 0, 0, clock.getZone().getRules().getOffset(Instant.now(clock)));
        when(bookingRepository.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                anyCollection(), any(), any()))
                .thenReturn(List.of(Booking.builder()
                        .createdAt(firstDay)
                        .grossAmount(450_000L)
                        .build()));

        var response = service.getOverview(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));

        assertThat(response.getPeriod().getFrom()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(response.getPeriod().getTo()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(response.getPeriod().getDays()).isEqualTo(2);
        assertThat(response.getGmvTrend()).hasSize(2);
        assertThat(response.getGmvTrend().get(0).getValue()).isEqualByComparingTo(BigDecimal.valueOf(450_000));

        ArgumentCaptor<OffsetDateTime> bookingStartCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> bookingEndCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(bookingRepository).findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                anyCollection(), bookingStartCaptor.capture(), bookingEndCaptor.capture());
        assertThat(bookingStartCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(bookingEndCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 22));

        ArgumentCaptor<Instant> revenueStartCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> revenueEndCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(platformRevenueRepository, times(2)).sumAmountByCreatedAtBetween(
                revenueStartCaptor.capture(), revenueEndCaptor.capture());
        assertThat(revenueStartCaptor.getAllValues().get(0).atZone(clock.getZone()).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(revenueEndCaptor.getAllValues().get(0).atZone(clock.getZone()).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    void reusesCachedDashboardForSameRange() {
        when(bookingRepository.findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                anyCollection(), any(), any()))
                .thenReturn(List.of());

        var first = service.getOverview(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));
        var second = service.getOverview(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));

        assertThat(second).isSameAs(first);
        verify(bookingRepository, times(1))
                .findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        anyCollection(), any(), any());
        verify(adminWalletRepository, times(1)).findById(eq(AdminWallet.PLATFORM_ADMIN_WALLET_ID));
    }
}
