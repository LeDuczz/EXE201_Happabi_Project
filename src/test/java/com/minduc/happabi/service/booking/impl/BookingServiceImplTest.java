package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.dto.request.booking.CreateBookingRequest;
import com.minduc.happabi.dto.response.booking.BookingResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingSlot;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.ServiceOfferingType;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSlotRepository bookingSlotRepository;

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @Mock
    private UserAccountLookupService userAccountLookupService;

    @Mock
    private IServiceEligibilityService serviceEligibilityService;

    @Mock
    private INotificationPublisher notificationPublisher;

    private BookingServiceImpl service;
    private User mother;
    private NurseProfile nurse;
    private ServiceOffering offering;
    private BookingSlot slot;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(
                bookingRepository,
                bookingSlotRepository,
                nurseProfileRepository,
                serviceOfferingRepository,
                userAccountLookupService,
                serviceEligibilityService,
                notificationPublisher);
        ReflectionTestUtils.setField(service, "paymentTtlMinutes", 15L);

        mother = User.builder().id(UUID.randomUUID()).fullName("Mother").build();
        nurse = NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).fullName("Nurse A").build())
                .nurseStatus(NurseStatus.ACTIVE)
                .availabilityStatus(AvailabilityStatus.AVAILABLE)
                .build();
        offering = ServiceOffering.builder()
                .id(UUID.randomUUID())
                .serviceType(ServiceOfferingType.SINGLE)
                .serviceName("Newborn bath")
                .durationMinutes(60)
                .grossAmount(180000L)
                .platformFeeAmount(27000L)
                .nurseEarningAmount(153000L)
                .isActive(true)
                .build();
        slot = BookingSlot.builder()
                .id(UUID.randomUUID())
                .nurseProfile(nurse)
                .startAt(OffsetDateTime.of(2026, 6, 18, 9, 0, 0, 0, ZoneOffset.UTC))
                .status(BookingSlotStatus.AVAILABLE)
                .build();
        request = new CreateBookingRequest();
        request.setNurseProfileId(nurse.getId());
        request.setServiceOfferingId(offering.getId());
        request.setStartAt(slot.getStartAt());
        request.setServiceAddress("  12 Nguyen Hue  ");
        request.setMotherNote("  please be on time  ");
    }

    @Test
    void createBookingCreatesPendingPaymentBookingWithLockedSlot() {
        mockHappyPath();
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            return booking;
        });

        BookingResponse response = service.createBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(response.getBookingId()).isNotNull();
        assertThat(response.getSlotId()).isEqualTo(slot.getId());
        assertThat(response.getDepositAmount()).isEqualTo(54000L);
        assertThat(response.getRemainingCashAmount()).isEqualTo(126000L);
        assertThat(response.getAppPaymentAmount()).isEqualTo(54000L);
        assertThat(response.getPaymentOption()).isEqualTo(BookingPaymentOption.DEPOSIT_30_PERCENT);
        assertThat(response.getServiceAddress()).isEqualTo("12 Nguyen Hue");
        assertThat(slot.getStatus()).isEqualTo(BookingSlotStatus.BOOKED);
        verify(notificationPublisher).publish(
                eq(mother.getId()),
                eq(NotificationType.BOOKING_PAYMENT_PENDING),
                any(),
                any(),
                eq("BOOKING"),
                eq(response.getBookingId().toString()));
    }

    @Test
    void createBookingSupportsFullAppPayment() {
        request.setPaymentOption(BookingPaymentOption.FULL_APP_PAYMENT);
        mockHappyPath();
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            return booking;
        });

        BookingResponse response = service.createBooking(request);

        assertThat(response.getDepositAmount()).isEqualTo(180000L);
        assertThat(response.getRemainingCashAmount()).isZero();
        assertThat(response.getAppPaymentAmount()).isEqualTo(180000L);
    }

    @Test
    void createBookingRejectsUnavailableNurse() {
        nurse.setAvailabilityStatus(AvailabilityStatus.OFFLINE);
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.NURSE_NOT_AVAILABLE);
    }

    @Test
    void createBookingRejectsUnknownActiveNurse() {
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.NURSE_PUBLIC_PROFILE_NOT_FOUND);
    }

    @Test
    void createBookingRejectsPackageService() {
        offering.setServiceType(null);
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.SERVICE_OFFERING_NOT_FOUND);
    }

    @Test
    void createBookingRejectsStartTimeNotAlignedToHour() {
        request.setStartAt(request.getStartAt().plusMinutes(30));
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceEligibilityService.isEligibleForService(nurse, offering)).thenReturn(true);

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.BOOKING_SLOT_INVALID);
        verify(bookingSlotRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void createBookingRejectsAlreadyBookedSlot() {
        mockHappyPath();
        slot.setStatus(BookingSlotStatus.BOOKED);

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBookingRejectsOverlappingBookingInsideDuration() {
        offering.setDurationMinutes(180);
        BookingSlot tenOClock = slotAt(request.getStartAt().plusHours(1));
        BookingSlot elevenOClock = slotAt(request.getStartAt().plusHours(2));
        tenOClock.setStatus(BookingSlotStatus.BOOKED);
        mockHappyPath();
        when(bookingSlotRepository.findByNurseProfileIdAndStartAtForUpdate(nurse.getId(), tenOClock.getStartAt()))
                .thenReturn(Optional.of(tenOClock));
        when(bookingSlotRepository.findByNurseProfileIdAndStartAtForUpdate(nurse.getId(), elevenOClock.getStartAt()))
                .thenReturn(Optional.of(elevenOClock));

        assertThatThrownBy(() -> service.createBooking(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    private void mockHappyPath() {
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceEligibilityService.isEligibleForService(nurse, offering)).thenReturn(true);
        when(bookingRepository.existsByNurseProfile_IdAndStartAtAndStatusIn(eq(nurse.getId()), eq(request.getStartAt()), anyCollection()))
                .thenReturn(false);
        when(bookingSlotRepository.findByNurseProfileIdAndStartAtForUpdate(nurse.getId(), request.getStartAt()))
                .thenReturn(Optional.of(slot));
    }

    private BookingSlot slotAt(OffsetDateTime startAt) {
        return BookingSlot.builder()
                .id(UUID.randomUUID())
                .nurseProfile(nurse)
                .startAt(startAt)
                .status(BookingSlotStatus.AVAILABLE)
                .build();
    }
}

