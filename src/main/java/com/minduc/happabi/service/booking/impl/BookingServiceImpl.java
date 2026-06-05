package com.minduc.happabi.service.booking.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.request.booking.CreateBookingDraftRequest;
import com.minduc.happabi.dto.response.booking.BookingDraftResponse;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.ServiceOfferingRepository;
import com.minduc.happabi.service.booking.IBookingService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
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
    private final NurseProfileRepository nurseProfileRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.booking.hold-ttl-minutes:15}")
    private long holdTtlMinutes;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    public BookingDraftResponse createDraft(CreateBookingDraftRequest request) {
        User mother = userAccountLookupService.getCurrentUser();
        NurseProfile nurseProfile = getBookableNurse(request);
        ServiceOffering serviceOffering = getActiveService(request);
        OffsetDateTime endAt = calculateEndAt(request.getStartAt(), serviceOffering);

        boolean alreadyBooked = bookingRepository.existsByNurseProfile_IdAndStartAtAndStatusIn(
                nurseProfile.getId(), request.getStartAt(), BLOCKING_STATUSES);
        if (alreadyBooked) {
            throw new AppException(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED);
        }

        OffsetDateTime holdExpiresAt = OffsetDateTime.now().plusMinutes(holdTtlMinutes);
        UUID draftId = UUID.randomUUID();
        String holdKey = holdKey(nurseProfile, request.getStartAt());
        String draftKey = draftKey(draftId);
        String holdValue = mother.getId().toString();
        Boolean held = stringRedisTemplate.opsForValue()
                .setIfAbsent(holdKey, holdValue, Duration.ofMinutes(holdTtlMinutes));
        if (!Boolean.TRUE.equals(held)) {
            throw new AppException(BookingErrorCode.BOOKING_SLOT_ALREADY_HELD);
        }

        try {
            BookingDraftCache draft = new BookingDraftCache(
                    draftId,
                    mother.getId(),
                    nurseProfile.getId(),
                    nurseProfile.getUser().getFullName(),
                    serviceOffering.getId(),
                    serviceOffering.getServiceName(),
                    request.getStartAt(),
                    endAt,
                    holdExpiresAt,
                    serviceOffering.getGrossAmount(),
                    serviceOffering.getPlatformFeeAmount(),
                    serviceOffering.getNurseEarningAmount(),
                    request.getServiceAddress().trim(),
                    normalize(request.getMotherNote()),
                    holdKey
            );
            stringRedisTemplate.opsForValue()
                    .set(draftKey, objectMapper.writeValueAsString(draft), Duration.ofMinutes(holdTtlMinutes));

            return BookingDraftResponse.builder()
                    .draftId(draftId)
                    .nurseProfileId(nurseProfile.getId())
                    .nurseName(nurseProfile.getUser().getFullName())
                    .serviceOfferingId(serviceOffering.getId())
                    .serviceName(serviceOffering.getServiceName())
                    .status(BookingStatus.DRAFT)
                    .startAt(request.getStartAt())
                    .endAt(endAt)
                    .holdExpiresAt(holdExpiresAt)
                    .grossAmount(serviceOffering.getGrossAmount())
                    .serviceAddress(request.getServiceAddress().trim())
                    .motherNote(normalize(request.getMotherNote()))
                    .build();
        } catch (JsonProcessingException e) {
            stringRedisTemplate.delete(holdKey);
            stringRedisTemplate.delete(draftKey);
            throw new AppException(BookingErrorCode.BOOKING_DRAFT_CREATE_FAILED, e);
        }
    }

    private NurseProfile getBookableNurse(CreateBookingDraftRequest request) {
        NurseProfile nurseProfile = nurseProfileRepository.findByIdAndNurseStatus(
                        request.getNurseProfileId(), NurseStatus.ACTIVE)
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PUBLIC_PROFILE_NOT_FOUND));
        if (nurseProfile.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new AppException(BookingErrorCode.NURSE_NOT_AVAILABLE);
        }
        return nurseProfile;
    }

    private ServiceOffering getActiveService(CreateBookingDraftRequest request) {
        return serviceOfferingRepository.findById(request.getServiceOfferingId())
                .filter(service -> Boolean.TRUE.equals(service.getIsActive()))
                .orElseThrow(() -> new AppException(BookingErrorCode.SERVICE_OFFERING_NOT_FOUND));
    }

    private OffsetDateTime calculateEndAt(OffsetDateTime startAt, ServiceOffering serviceOffering) {
        if (serviceOffering.getDurationDays() != null && serviceOffering.getDurationDays() > 0) {
            return startAt.plusDays(serviceOffering.getDurationDays());
        }
        int minutes = serviceOffering.getDurationMinutes() == null || serviceOffering.getDurationMinutes() <= 0
                ? 60
                : serviceOffering.getDurationMinutes();
        return startAt.plusMinutes(minutes);
    }

    private String holdKey(NurseProfile nurseProfile, OffsetDateTime startAt) {
        return "booking:hold:nurse:%s:%s".formatted(nurseProfile.getId(), startAt.toInstant());
    }

    private String draftKey(UUID draftId) {
        return "booking:draft:%s".formatted(draftId);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record BookingDraftCache(
            UUID draftId,
            UUID motherId,
            UUID nurseProfileId,
            String nurseName,
            UUID serviceOfferingId,
            String serviceName,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            OffsetDateTime holdExpiresAt,
            Long grossAmount,
            Long platformFeeAmount,
            Long nurseEarningAmount,
            String serviceAddress,
            String motherNote,
            String holdKey
    ) {
    }
}
