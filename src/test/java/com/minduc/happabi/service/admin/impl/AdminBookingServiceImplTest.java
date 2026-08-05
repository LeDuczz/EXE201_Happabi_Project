package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.dto.request.admin.AdminBookingSearchCriteria;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.repository.BookingRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
                AdminBookingSearchCriteria.builder()
                        .query(" ngoc ")
                        .status(BookingStatus.COMPLETED)
                        .paymentOption(BookingPaymentOption.DEPOSIT_30_PERCENT)
                        .createdFrom(OffsetDateTime.parse("2026-07-14T00:00:00+07:00"))
                        .createdTo(OffsetDateTime.parse("2026-07-21T00:00:00+07:00"))
                        .serviceFrom(OffsetDateTime.parse("2026-07-16T00:00:00+07:00"))
                        .serviceTo(OffsetDateTime.parse("2026-07-17T00:00:00+07:00"))
                        .build(),
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

        var page = service.getBookings(AdminBookingSearchCriteria.builder().query("   ").build(), pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(bookingRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Booking>>any(),
                eq(pageable));
    }

    @Test
    void getBookingsMapsMissingRelationsWithoutFailing() {
        Pageable pageable = Pageable.unpaged();
        Booking sparseBooking = Booking.builder()
                .id(UUID.randomUUID())
                .bookingKey("W9-PROD-SPARSE")
                .status(BookingStatus.PENDING_PAYMENT)
                .paymentOption(BookingPaymentOption.FULL_APP_PAYMENT)
                .createdAt(OffsetDateTime.parse("2026-07-14T13:00:00+07:00"))
                .grossAmount(390000L)
                .build();
        when(bookingRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Booking>>any(),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sparseBooking)));

        var page = service.getBookings(AdminBookingSearchCriteria.builder().build(), pageable);

        var response = page.getContent().get(0);
        assertThat(response.getBookingKey()).isEqualTo("W9-PROD-SPARSE");
        assertThat(response.getMother()).isNull();
        assertThat(response.getNurse()).isNull();
        assertThat(response.getService()).isNull();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void buildSpecificationAppliesSearchAndRangePredicates() {
        var criteria = AdminBookingSearchCriteria.builder()
                .query(" Ngoc ")
                .status(BookingStatus.COMPLETED)
                .paymentOption(BookingPaymentOption.DEPOSIT_30_PERCENT)
                .createdFrom(OffsetDateTime.parse("2026-07-14T00:00:00+07:00"))
                .createdTo(OffsetDateTime.parse("2026-07-21T00:00:00+07:00"))
                .serviceFrom(OffsetDateTime.parse("2026-07-16T00:00:00+07:00"))
                .serviceTo(OffsetDateTime.parse("2026-07-17T00:00:00+07:00"))
                .build();
        Root<Booking> root = mock(Root.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Join mother = mock(Join.class);
        Join nurseProfile = mock(Join.class);
        Join nurseUser = mock(Join.class);
        Join serviceOffering = mock(Join.class);
        Fetch nurseFetch = mock(Fetch.class);
        Path stringPath = mock(Path.class);
        Path statusPath = mock(Path.class);
        Path paymentPath = mock(Path.class);
        Path<OffsetDateTime> createdAtPath = mock(Path.class);
        Path<OffsetDateTime> startAtPath = mock(Path.class);
        Expression<String> lowered = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);

        when(criteriaQuery.getResultType()).thenReturn((Class) Booking.class);
        when(root.fetch("mother", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(mock(Fetch.class));
        when(root.fetch("nurseProfile", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(nurseFetch);
        when(nurseFetch.fetch("user", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(mock(Fetch.class));
        when(root.fetch("serviceOffering", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(mock(Fetch.class));
        when(root.join("mother", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(mother);
        when(root.join("nurseProfile", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(nurseProfile);
        when(nurseProfile.join("user", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(nurseUser);
        when(root.join("serviceOffering", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(serviceOffering);
        when(root.get("bookingKey")).thenReturn(stringPath);
        when(root.get("serviceAddress")).thenReturn(stringPath);
        when(root.get("status")).thenReturn(statusPath);
        when(root.get("paymentOption")).thenReturn(paymentPath);
        when(root.<OffsetDateTime>get("createdAt")).thenReturn(createdAtPath);
        when(root.<OffsetDateTime>get("startAt")).thenReturn(startAtPath);
        when(mother.get("fullName")).thenReturn(stringPath);
        when(mother.get("phone")).thenReturn(stringPath);
        when(mother.get("email")).thenReturn(stringPath);
        when(nurseUser.get("fullName")).thenReturn(stringPath);
        when(nurseUser.get("phone")).thenReturn(stringPath);
        when(nurseUser.get("email")).thenReturn(stringPath);
        when(serviceOffering.get("serviceName")).thenReturn(stringPath);
        when(criteriaBuilder.lower(org.mockito.ArgumentMatchers.<Expression<String>>any())).thenReturn(lowered);
        when(criteriaBuilder.like(eq(lowered), eq("%ngoc%"))).thenReturn(predicate);
        doReturn(predicate).when(criteriaBuilder).or(org.mockito.ArgumentMatchers.<Predicate[]>any());
        when(criteriaBuilder.equal(statusPath, BookingStatus.COMPLETED)).thenReturn(predicate);
        when(criteriaBuilder.equal(paymentPath, BookingPaymentOption.DEPOSIT_30_PERCENT)).thenReturn(predicate);
        when(criteriaBuilder.greaterThanOrEqualTo(eq(createdAtPath), org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.lessThan(eq(createdAtPath), org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.greaterThanOrEqualTo(eq(startAtPath), org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.lessThan(eq(startAtPath), org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(predicate);
        doReturn(predicate).when(criteriaBuilder).and(org.mockito.ArgumentMatchers.<Predicate[]>any());

        service.buildSpecification(criteria).toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(criteriaQuery).distinct(true);
        verify(root).fetch("mother", jakarta.persistence.criteria.JoinType.LEFT);
        verify(criteriaBuilder, atLeastOnce()).like(eq(lowered), eq("%ngoc%"));
        verify(criteriaBuilder).equal(statusPath, BookingStatus.COMPLETED);
        verify(criteriaBuilder).equal(paymentPath, BookingPaymentOption.DEPOSIT_30_PERCENT);
        verify(criteriaBuilder, atLeastOnce()).greaterThanOrEqualTo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
        verify(criteriaBuilder, atLeastOnce()).lessThan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void buildSpecificationSkipsFetchForCountQueriesAndBlankFilters() {
        Root<Booking> root = mock(Root.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Join mother = mock(Join.class);
        Join nurseProfile = mock(Join.class);
        Join nurseUser = mock(Join.class);
        Join serviceOffering = mock(Join.class);
        Predicate predicate = mock(Predicate.class);

        when(criteriaQuery.getResultType()).thenReturn((Class) Long.class);
        when(root.join("mother", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(mother);
        when(root.join("nurseProfile", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(nurseProfile);
        when(nurseProfile.join("user", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(nurseUser);
        when(root.join("serviceOffering", jakarta.persistence.criteria.JoinType.LEFT)).thenReturn(serviceOffering);
        when(root.get("createdAt")).thenReturn(mock(Path.class));
        when(root.get("startAt")).thenReturn(mock(Path.class));
        doReturn(predicate).when(criteriaBuilder).and(org.mockito.ArgumentMatchers.<Predicate[]>any());

        service.buildSpecification(AdminBookingSearchCriteria.builder().query(" ").build())
                .toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(root, never()).fetch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(criteriaBuilder, never()).like(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
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
