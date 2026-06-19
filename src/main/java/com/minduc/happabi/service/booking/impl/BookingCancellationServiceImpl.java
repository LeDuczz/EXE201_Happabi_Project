package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.dto.request.booking.CancelBookingRequest;
import com.minduc.happabi.dto.response.booking.BookingCancellationResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingCancellation;
import com.minduc.happabi.entity.MotherRefundRequest;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.BookingCancellationActor;
import com.minduc.happabi.enums.BookingSlotStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.MotherRefundStatus;
import com.minduc.happabi.enums.NotificationType;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.BookingCancellationRepository;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.BookingSlotRepository;
import com.minduc.happabi.repository.MotherRefundRequestRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.WorkSessionRepository;
import com.minduc.happabi.service.booking.IBookingCancellationService;
import com.minduc.happabi.service.notification.INotificationPublisher;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCancellationServiceImpl implements IBookingCancellationService {

    private static final List<BookingStatus> CANCELLABLE_BOOKING_STATUSES = List.of(
            BookingStatus.PENDING_PAYMENT,
            BookingStatus.PENDING_NURSE_ACCEPTANCE,
            BookingStatus.ACCEPTED
    );

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final WorkSessionRepository workSessionRepository;
    private final BookingCancellationRepository bookingCancellationRepository;
    private final MotherRefundRequestRepository refundRequestRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final UserRepository userRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final INotificationPublisher notificationPublisher;

    @Value("${app.booking.cancellation-free-hours:24}")
    private long freeCancellationHours;

    @Override
    @LogExecution
    @TimedAction("CANCEL_BOOKING_BY_MOTHER")
    @AuditAction(action = "CANCEL_BOOKING_BY_MOTHER", resourceType = "BOOKING")
    @Transactional
    @PreAuthorize("hasRole('MOTHER')")
    public BookingCancellationResponse cancelByMother(UUID bookingId, CancelBookingRequest request) {
        User mother = userAccountLookupService.getCurrentUser();
        Booking booking = lockBooking(bookingId);
        if (!booking.getMother().getId().equals(mother.getId())) {
            throw new AppException(BookingErrorCode.BOOKING_ACCESS_DENIED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean refundable = isBeforeFreeCancellationCutoff(booking, now) && isPaidBooking(booking);
        BookingCancellation cancellation = cancelBooking(
                booking,
                mother,
                BookingCancellationActor.MOTHER,
                cleanReason(request),
                refundable,
                refundable ? booking.getAppPaymentAmount() : 0L
        );
        notifyNurse(booking,
                "Booking cancelled by mother",
                "The mother cancelled booking %s. Reason: %s".formatted(booking.getId(), cancellation.getReason()));
        notifyMother(booking,
                refundable ? "Booking cancelled - refund pending" : "Booking cancelled",
                refundable
                        ? "Your booking was cancelled and a manual refund request has been created."
                        : "Your booking was cancelled. This cancellation is not refundable by policy.");
        return toResponse(cancellation);
    }

    @Override
    @LogExecution
    @TimedAction("CANCEL_BOOKING_BY_NURSE")
    @AuditAction(action = "CANCEL_BOOKING_BY_NURSE", resourceType = "BOOKING")
    @Transactional
    @PreAuthorize("hasRole('NURSE') and @nurseAccessGuard.isActive(authentication)")
    public BookingCancellationResponse cancelByNurse(UUID bookingId, CancelBookingRequest request) {
        NurseProfile nurseProfile = currentNurseProfile();
        Booking booking = lockBooking(bookingId);
        if (!booking.getNurseProfile().getId().equals(nurseProfile.getId())) {
            throw new AppException(BookingErrorCode.BOOKING_ACCESS_DENIED);
        }
        if (!isBeforeFreeCancellationCutoff(booking, OffsetDateTime.now())) {
            throw new AppException(BookingErrorCode.BOOKING_CANCELLATION_TOO_LATE);
        }

        BookingCancellation cancellation = cancelBooking(
                booking,
                nurseProfile.getUser(),
                BookingCancellationActor.NURSE,
                cleanReason(request),
                isPaidBooking(booking),
                isPaidBooking(booking) ? booking.getAppPaymentAmount() : 0L
        );
        notifyMother(booking,
                "Booking cancelled by nurse",
                "Your nurse cancelled this booking. A manual refund request has been created if payment was already captured.");
        notifyNurse(booking,
                "Booking cancellation recorded",
                "Your cancellation was recorded. It may affect your performance metrics.");
        return toResponse(cancellation);
    }

    private BookingCancellation cancelBooking(Booking booking,
                                              User actor,
                                              BookingCancellationActor actorType,
                                              String reason,
                                              boolean refundable,
                                              Long refundableAmount) {
        if (bookingCancellationRepository.existsByBooking_Id(booking.getId())) {
            throw new AppException(BookingErrorCode.BOOKING_ALREADY_CANCELLED);
        }
        if (!CANCELLABLE_BOOKING_STATUSES.contains(booking.getStatus())) {
            throw new AppException(BookingErrorCode.BOOKING_CANCELLATION_NOT_ALLOWED);
        }

        OffsetDateTime cutoffAt = booking.getStartAt().minusHours(freeCancellationHours);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        bookingSlotRepository.releaseByBookingId(booking.getId(), BookingSlotStatus.AVAILABLE);
        workSessionRepository.findByBookingIdForUpdate(booking.getId()).ifPresent(session -> {
            if (session.getStatus() != WorkSessionStatus.COMPLETED
                    && session.getStatus() != WorkSessionStatus.AUTO_CONFIRMED) {
                session.setStatus(WorkSessionStatus.CANCELLED);
                workSessionRepository.save(session);
            }
        });

        BookingCancellation cancellation = bookingCancellationRepository.save(BookingCancellation.builder()
                .booking(booking)
                .cancelledBy(actor)
                .actor(actorType)
                .reason(reason)
                .refundable(refundable)
                .refundableAmount(refundableAmount == null ? 0L : refundableAmount)
                .policyCutoffAt(cutoffAt)
                .build());
        createRefundRequestIfNeeded(booking, cancellation);
        log.info("[BookingCancellation] bookingId={} actor={} refundable={} amount={}",
                booking.getId(), actorType, cancellation.isRefundable(), cancellation.getRefundableAmount());
        return cancellation;
    }

    private void createRefundRequestIfNeeded(Booking booking, BookingCancellation cancellation) {
        if (!cancellation.isRefundable() || cancellation.getRefundableAmount() <= 0) {
            return;
        }
        refundRequestRepository.findByBooking_Id(booking.getId()).orElseGet(() -> {
            MotherRefundRequest refund = refundRequestRepository.save(MotherRefundRequest.builder()
                    .booking(booking)
                    .mother(booking.getMother())
                    .amount(cancellation.getRefundableAmount())
                    .status(MotherRefundStatus.PENDING)
                    .reason(cancellation.getReason())
                    .build());
            notifyAdminsRefundCreated(refund);
            return refund;
        });
    }

    private boolean isBeforeFreeCancellationCutoff(Booking booking, OffsetDateTime now) {
        return now.isBefore(booking.getStartAt().minusHours(freeCancellationHours));
    }

    private boolean isPaidBooking(Booking booking) {
        return booking.getStatus() == BookingStatus.ACCEPTED
                || booking.getStatus() == BookingStatus.PENDING_NURSE_ACCEPTANCE;
    }

    private Booking lockBooking(UUID bookingId) {
        return bookingRepository.findByIdForCancellationUpdate(bookingId)
                .orElseThrow(() -> new AppException(BookingErrorCode.BOOKING_NOT_FOUND));
    }

    private NurseProfile currentNurseProfile() {
        return nurseProfileRepository.findByUser(userAccountLookupService.getCurrentUser())
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
    }

    private String cleanReason(CancelBookingRequest request) {
        String reason = request == null ? null : request.getReason();
        if (reason == null || reason.isBlank()) {
            throw new AppException(BookingErrorCode.BOOKING_CANCELLATION_NOT_ALLOWED, "Cancellation reason is required.");
        }
        return reason.trim();
    }

    private void notifyAdminsRefundCreated(MotherRefundRequest refund) {
        userRepository.findActiveUsersByRoleName(UserRole.ADMIN).forEach(admin ->
                notificationPublisher.publish(
                        admin.getId(),
                        NotificationType.WORK_SESSION_UPDATED,
                        "Mother refund request created",
                        "A refund request of %s VND was created for booking %s."
                                .formatted(refund.getAmount(), refund.getBooking().getId()),
                        "MOTHER_REFUND",
                        refund.getId().toString()));
    }

    private void notifyMother(Booking booking, String title, String message) {
        notificationPublisher.publish(
                booking.getMother().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "BOOKING",
                booking.getId().toString());
    }

    private void notifyNurse(Booking booking, String title, String message) {
        notificationPublisher.publish(
                booking.getNurseProfile().getUser().getId(),
                NotificationType.WORK_SESSION_UPDATED,
                title,
                message,
                "BOOKING",
                booking.getId().toString());
    }

    private BookingCancellationResponse toResponse(BookingCancellation cancellation) {
        return BookingCancellationResponse.builder()
                .id(cancellation.getId())
                .bookingId(cancellation.getBooking().getId())
                .actor(cancellation.getActor())
                .reason(cancellation.getReason())
                .refundable(cancellation.isRefundable())
                .refundableAmount(cancellation.getRefundableAmount())
                .policyCutoffAt(cancellation.getPolicyCutoffAt())
                .createdAt(cancellation.getCreatedAt())
                .build();
    }
}
