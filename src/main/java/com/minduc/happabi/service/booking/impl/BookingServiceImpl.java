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
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.service.booking.IBookingService;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements IBookingService {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING_PAYMENT,
            BookingStatus.PENDING_NURSE_ACCEPTANCE,
            BookingStatus.ACCEPTED
    );

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final IServiceEligibilityService serviceEligibilityService;
    private final INotificationPublisher notificationPublisher;

    @Value("${app.booking.payment-ttl-minutes:15}")
    private long paymentTtlMinutes;

    @Override
    @LogExecution
    @TimedAction("CREATE_BOOKING")
    @AuditAction(action = "CREATE_BOOKING", resourceType = "BOOKING")
    @Transactional
    @PreAuthorize("hasRole('MOTHER')")
    public BookingResponse createBooking(CreateBookingRequest request) {
        User mother = userAccountLookupService.getCurrentUser();
        NurseProfile nurseProfile = getBookableNurse(request);
        ServiceOffering serviceOffering = getActiveService(request);
        if (!serviceEligibilityService.isEligibleForService(nurseProfile, serviceOffering)) {
            throw new AppException(BookingErrorCode.NURSE_SKILL_NOT_ELIGIBLE);
        }
        OffsetDateTime startAt = validateSlotStart(request.getStartAt());
        OffsetDateTime endAt = calculateEndAt(startAt, serviceOffering);

        boolean alreadyBooked = bookingRepository.existsByNurseProfile_IdAndStartAtAndStatusIn(
                nurseProfile.getId(), startAt, BLOCKING_STATUSES);
        if (alreadyBooked) {
            throw new AppException(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED);
        }

        List<BookingSlot> slots = getOrCreateSlotsForUpdate(nurseProfile, startAt, endAt);
        if (slots.stream().anyMatch(this::isUnavailableSlot)) {
            throw new AppException(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED);
        }
        BookingSlot primarySlot = slots.getFirst();

        OffsetDateTime paymentExpiresAt = OffsetDateTime.now().plusMinutes(paymentTtlMinutes);
        BookingPaymentOption paymentOption = request.getPaymentOption() == null
                ? BookingPaymentOption.DEPOSIT_30_PERCENT
                : request.getPaymentOption();
        PaymentBreakdown paymentBreakdown = calculatePaymentBreakdown(serviceOffering.getGrossAmount(), paymentOption);
        Booking booking = Booking.builder()
                .mother(mother)
                .nurseProfile(nurseProfile)
                .serviceOffering(serviceOffering)
                .slot(primarySlot)
                .status(BookingStatus.PENDING_PAYMENT)
                .startAt(startAt)
                .endAt(endAt)
                .paymentExpiresAt(paymentExpiresAt)
                .grossAmount(serviceOffering.getGrossAmount())
                .platformFeeAmount(serviceOffering.getPlatformFeeAmount())
                .nurseEarningAmount(serviceOffering.getNurseEarningAmount())
                .paymentOption(paymentOption)
                .depositAmount(paymentBreakdown.depositAmount())
                .remainingCashAmount(paymentBreakdown.remainingCashAmount())
                .appPaymentAmount(paymentBreakdown.appPaymentAmount())
                .serviceAddress(request.getServiceAddress().trim())
                .motherNote(normalize(request.getMotherNote()))
                .bookingKey(bookingKey(nurseProfile, startAt))
                .build();
        try {
            booking = bookingRepository.saveAndFlush(booking);
            for (BookingSlot slot : slots) {
                slot.setStatus(BookingSlotStatus.BOOKED);
                slot.setBooking(booking);
            }
            notifyMotherPaymentPending(booking);
            log.info("[Booking] Created pending payment booking id={} nurseId={} motherId={} startAt={} endAt={} appPaymentAmount={}",
                    booking.getId(), nurseProfile.getId(), mother.getId(), startAt, endAt, booking.getAppPaymentAmount());
            return toResponse(booking, primarySlot, nurseProfile, serviceOffering);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Booking] Slot conflict while creating booking nurseId={} startAt={} endAt={}",
                    nurseProfile.getId(), startAt, endAt);
            throw new AppException(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED, e);
        }
    }

    @Override
    @LogExecution
    @TimedAction("GET_MY_PENDING_BOOKING_PAYMENTS")
    @AuditAction(action = "GET_MY_PENDING_BOOKING_PAYMENTS", resourceType = "BOOKING")
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    public List<BookingResponse> getMyPendingPayments() {
        User mother = userAccountLookupService.getCurrentUser();
        return bookingRepository.findPendingPaymentsByMotherId(
                        mother.getId(), BookingStatus.PENDING_PAYMENT, OffsetDateTime.now())
                .stream()
                .map(booking -> toResponse(
                        booking,
                        booking.getSlot(),
                        booking.getNurseProfile(),
                        booking.getServiceOffering()))
                .toList();
    }

    private NurseProfile getBookableNurse(CreateBookingRequest request) {
        NurseProfile nurseProfile = nurseProfileRepository.findByIdAndNurseStatus(
                        request.getNurseProfileId(), NurseStatus.ACTIVE)
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PUBLIC_PROFILE_NOT_FOUND));
        if (nurseProfile.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new AppException(BookingErrorCode.NURSE_NOT_AVAILABLE);
        }
        return nurseProfile;
    }

    private ServiceOffering getActiveService(CreateBookingRequest request) {
        return serviceOfferingRepository.findById(request.getServiceOfferingId())
                .filter(service -> Boolean.TRUE.equals(service.getIsActive()))
                .filter(service -> service.getServiceType() == ServiceOfferingType.SINGLE)
                .orElseThrow(() -> new AppException(BookingErrorCode.SERVICE_OFFERING_NOT_FOUND));
    }

    private OffsetDateTime calculateEndAt(OffsetDateTime startAt, ServiceOffering serviceOffering) {
        int minutes = serviceOffering.getDurationMinutes() == null || serviceOffering.getDurationMinutes() <= 0
                ? 60
                : serviceOffering.getDurationMinutes();
        return startAt.plusMinutes(minutes);
    }

    private OffsetDateTime validateSlotStart(OffsetDateTime startAt) {
        OffsetDateTime truncated = startAt.truncatedTo(ChronoUnit.HOURS);
        if (!startAt.equals(truncated)) {
            throw new AppException(BookingErrorCode.BOOKING_SLOT_INVALID);
        }
        return startAt;
    }

    private List<BookingSlot> getOrCreateSlotsForUpdate(NurseProfile nurseProfile,
                                                        OffsetDateTime startAt,
                                                        OffsetDateTime endAt) {
        List<OffsetDateTime> slotStarts = hourlySlotStarts(startAt, endAt);
        for (OffsetDateTime slotStart : slotStarts) {
            bookingSlotRepository.insertIfAbsent(UUID.randomUUID(), nurseProfile.getId(), slotStart);
        }

        List<BookingSlot> slots = new ArrayList<>(slotStarts.size());
        for (OffsetDateTime slotStart : slotStarts) {
            slots.add(bookingSlotRepository.findByNurseProfileIdAndStartAtForUpdate(nurseProfile.getId(), slotStart)
                    .orElseThrow(() -> new AppException(BookingErrorCode.BOOKING_CREATE_FAILED)));
        }
        return slots;
    }

    private List<OffsetDateTime> hourlySlotStarts(OffsetDateTime startAt, OffsetDateTime endAt) {
        List<OffsetDateTime> slotStarts = new ArrayList<>();
        OffsetDateTime cursor = startAt;
        while (cursor.isBefore(endAt)) {
            slotStarts.add(cursor);
            cursor = cursor.plusHours(1);
        }
        return slotStarts;
    }

    private boolean isUnavailableSlot(BookingSlot slot) {
        return slot.getStatus() == BookingSlotStatus.BOOKED || slot.getBooking() != null;
    }

    private PaymentBreakdown calculatePaymentBreakdown(Long grossAmount, BookingPaymentOption paymentOption) {
        long amount = grossAmount == null ? 0L : grossAmount;
        if (paymentOption == BookingPaymentOption.FULL_APP_PAYMENT) {
            return new PaymentBreakdown(amount, 0L, amount);
        }
        long depositAmount = Math.round(amount * 0.3d);
        return new PaymentBreakdown(depositAmount, amount - depositAmount, depositAmount);
    }

    private BookingResponse toResponse(Booking booking,
                                            BookingSlot slot,
                                            NurseProfile nurseProfile,
                                            ServiceOffering serviceOffering) {
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .slotId(slot.getId())
                .nurseProfileId(nurseProfile.getId())
                .nurseName(nurseProfile.getUser().getFullName())
                .serviceOfferingId(serviceOffering.getId())
                .serviceName(serviceOffering.getServiceName())
                .status(booking.getStatus())
                .startAt(booking.getStartAt())
                .endAt(booking.getEndAt())
                .paymentExpiresAt(booking.getPaymentExpiresAt())
                .grossAmount(booking.getGrossAmount())
                .depositAmount(booking.getDepositAmount())
                .remainingCashAmount(booking.getRemainingCashAmount())
                .appPaymentAmount(booking.getAppPaymentAmount())
                .paymentOption(booking.getPaymentOption())
                .serviceAddress(booking.getServiceAddress())
                .motherNote(booking.getMotherNote())
                .build();
    }

    private String bookingKey(NurseProfile nurseProfile, OffsetDateTime startAt) {
        return "booking:nurse:%s:%s".formatted(nurseProfile.getId(), startAt.toInstant());
    }

    private void notifyMotherPaymentPending(Booking booking) {
        notificationPublisher.publish(
                booking.getMother().getId(),
                NotificationType.BOOKING_PAYMENT_PENDING,
                "Booking is waiting for payment",
                "Your booking for %s has been created. Please complete payment before %s."
                        .formatted(booking.getServiceOffering().getServiceName(),
                                booking.getPaymentExpiresAt().toLocalTime()),
                "BOOKING",
                booking.getId().toString()
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record PaymentBreakdown(Long depositAmount, Long remainingCashAmount, Long appPaymentAmount) {
    }

}

