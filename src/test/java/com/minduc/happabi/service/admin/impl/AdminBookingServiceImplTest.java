package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    private AdminBookingServiceImpl service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new AdminBookingServiceImpl(bookingRepository);
        booking = buildBooking();
    }

    @Test
    void getBookingsReturnsMappedPagedBookings() {
        Pageable pageable = Pageable.unpaged();
        when(bookingRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Booking>>any(),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(booking)));

        var page = service.getBookings(
                " ngoc ",
                BookingStatus.COMPLETED,
                BookingPaymentOption.DEPOSIT_30_PERCENT,
                OffsetDateTime.parse("2026-07-14T00:00:00+07:00"),
                OffsetDateTime.parse("2026-07-21T00:00:00+07:00"),
                OffsetDateTime.parse("2026-07-16T00:00:00+07:00"),
                OffsetDateTime.parse("2026-07-17T00:00:00+07:00"),
                pageable);

        ArgumentCaptor<Specification<Booking>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(bookingRepository).findAll(specCaptor.capture(), eq(pageable));
        assertThat(specCaptor.getValue()).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(1);
        var response = page.getContent().get(0);
        assertThat(response.getBookingKey()).isEqualTo("W9-PROD-001");
        assertThat(response.getMother().getFullName()).isEqualTo("Le Bao Ngoc");
        assertThat(response.getNurse().getFullName()).isEqualTo("Pham Khanh Linh");
        assertThat(response.getService().getServiceName()).isEqualTo("Prenatal massage");
        assertThat(response.getGrossAmount()).isEqualTo(450000);
        assertThat(response.getPlatformFeeAmount()).isEqualTo(67500);
    }

    @Test
    void getBookingsTreatsBlankFiltersAsMissing() {
        Pageable pageable = Pageable.unpaged();
        when(bookingRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Booking>>any(),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(booking)));

        var page = service.getBookings("   ", null, null, null, null, null, null, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(bookingRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Booking>>any(),
                eq(pageable));
    }

    @Test
    void getBookingDetailReturnsMappedBooking() {
        UUID bookingId = booking.getId();
        when(bookingRepository.findByIdWithPaymentRelations(bookingId)).thenReturn(Optional.of(booking));

        var response = service.getBookingDetail(bookingId);

        verify(bookingRepository).findByIdWithPaymentRelations(bookingId);
        assertThat(response.getId()).isEqualTo(bookingId);
        assertThat(response.getPaymentOption()).isEqualTo(BookingPaymentOption.DEPOSIT_30_PERCENT);
        assertThat(response.getRemainingCashAmount()).isEqualTo(315000);
    }

    @Test
    void getBookingDetailThrowsWhenMissing() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findByIdWithPaymentRelations(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBookingDetail(bookingId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Booking was not found");
    }

    private Booking buildBooking() {
        User mother = User.builder()
                .id(UUID.randomUUID())
                .fullName("Le Bao Ngoc")
                .phone("+84910000003")
                .email("mother03@happabi.local")
                .build();
        User nurseUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Pham Khanh Linh")
                .phone("+84367270392")
                .email("nurse01@happabi.local")
                .build();
        NurseProfile nurseProfile = NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(nurseUser)
                .ratingAvg(BigDecimal.valueOf(4.8))
                .build();
        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(UUID.randomUUID())
                .serviceCode("W9_PRENATAL_RELAX_MASSAGE")
                .serviceType(ServiceOfferingType.SINGLE)
                .serviceName("Prenatal massage")
                .groupName("Mother care")
                .grossAmount(450000L)
                .platformFeeAmount(67500L)
                .nurseEarningAmount(382500L)
                .commissionRate(BigDecimal.valueOf(15))
                .build();
        return Booking.builder()
                .id(UUID.randomUUID())
                .bookingKey("W9-PROD-001")
                .status(BookingStatus.COMPLETED)
                .paymentOption(BookingPaymentOption.DEPOSIT_30_PERCENT)
                .mother(mother)
                .nurseProfile(nurseProfile)
                .serviceOffering(serviceOffering)
                .startAt(OffsetDateTime.parse("2026-07-16T14:00:00+07:00"))
                .endAt(OffsetDateTime.parse("2026-07-16T15:00:00+07:00"))
                .createdAt(OffsetDateTime.parse("2026-07-14T13:00:00+07:00"))
                .updatedAt(OffsetDateTime.parse("2026-07-16T15:15:00+07:00"))
                .grossAmount(450000L)
                .appPaymentAmount(135000L)
                .depositAmount(135000L)
                .remainingCashAmount(315000L)
                .platformFeeAmount(67500L)
                .nurseEarningAmount(382500L)
                .serviceAddress("S9.03 Vinhomes Grand Park")
                .motherNote("Seed production Week 9.")
                .build();
    }
}
