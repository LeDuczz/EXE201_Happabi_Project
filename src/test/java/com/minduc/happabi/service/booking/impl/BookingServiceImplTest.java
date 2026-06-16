package com.minduc.happabi.service.booking.impl;

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
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.user.UserAccountLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private NurseProfileRepository nurseProfileRepository;

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @Mock
    private UserAccountLookupService userAccountLookupService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private IServiceEligibilityService serviceEligibilityService;

    private BookingServiceImpl service;
    private User mother;
    private NurseProfile nurse;
    private ServiceOffering offering;
    private CreateBookingDraftRequest request;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(
                bookingRepository,
                nurseProfileRepository,
                serviceOfferingRepository,
                userAccountLookupService,
                stringRedisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                serviceEligibilityService);
        ReflectionTestUtils.setField(service, "holdTtlMinutes", 15L);

        mother = User.builder().id(UUID.randomUUID()).fullName("Mother").build();
        nurse = NurseProfile.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).fullName("Nurse A").build())
                .nurseStatus(NurseStatus.ACTIVE)
                .availabilityStatus(AvailabilityStatus.AVAILABLE)
                .build();
        offering = ServiceOffering.builder()
                .id(UUID.randomUUID())
                .serviceName("Newborn bath")
                .durationMinutes(45)
                .grossAmount(180000L)
                .platformFeeAmount(27000L)
                .nurseEarningAmount(153000L)
                .isActive(true)
                .build();
        request = new CreateBookingDraftRequest();
        request.setNurseProfileId(nurse.getId());
        request.setServiceOfferingId(offering.getId());
        request.setStartAt(OffsetDateTime.now().plusDays(1));
        request.setServiceAddress("  12 Nguyen Hue  ");
        request.setMotherNote("  please be on time  ");
    }

    @Test
    void createDraftCreatesRedisHoldAndReturnsDraftResponse() {
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceEligibilityService.isEligibleForService(nurse, offering)).thenReturn(true);
        when(bookingRepository.existsByNurseProfile_IdAndStartAtAndStatusIn(eq(nurse.getId()), eq(request.getStartAt()), anyCollection()))
                .thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq(mother.getId().toString()), eq(Duration.ofMinutes(15))))
                .thenReturn(true);

        BookingDraftResponse response = service.createDraft(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.DRAFT);
        assertThat(response.getNurseName()).isEqualTo("Nurse A");
        assertThat(response.getEndAt()).isEqualTo(request.getStartAt().plusMinutes(45));
        assertThat(response.getServiceAddress()).isEqualTo("12 Nguyen Hue");
        assertThat(response.getMotherNote()).isEqualTo("please be on time");
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void createDraftRejectsUnavailableNurse() {
        nurse.setAvailabilityStatus(AvailabilityStatus.OFFLINE);
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));

        assertThatThrownBy(() -> service.createDraft(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.NURSE_NOT_AVAILABLE);
    }

    @Test
    void createDraftRejectsUnknownActiveNurse() {
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.NURSE_PUBLIC_PROFILE_NOT_FOUND);
    }

    @Test
    void createDraftRejectsServiceWhenNurseIsNotEligible() {
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceEligibilityService.isEligibleForService(nurse, offering)).thenReturn(false);

        assertThatThrownBy(() -> service.createDraft(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.NURSE_SKILL_NOT_ELIGIBLE);
    }

    @Test
    void createDraftReleasesHoldWhenDraftSerializationFails() {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        service = new BookingServiceImpl(
                bookingRepository,
                nurseProfileRepository,
                serviceOfferingRepository,
                userAccountLookupService,
                stringRedisTemplate,
                failingMapper,
                serviceEligibilityService);
        ReflectionTestUtils.setField(service, "holdTtlMinutes", 15L);
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceEligibilityService.isEligibleForService(nurse, offering)).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq(mother.getId().toString()), eq(Duration.ofMinutes(15))))
                .thenReturn(true);
        try {
            when(failingMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError(e);
        }

        assertThatThrownBy(() -> service.createDraft(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.BOOKING_DRAFT_CREATE_FAILED);
        verify(stringRedisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("booking:hold:nurse:"));
        verify(stringRedisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("booking:draft:"));
    }

    @Test
    void createDraftDoesNotCreateHoldWhenSlotAlreadyBooked() {
        when(userAccountLookupService.getCurrentUser()).thenReturn(mother);
        when(nurseProfileRepository.findByIdAndNurseStatus(nurse.getId(), NurseStatus.ACTIVE)).thenReturn(Optional.of(nurse));
        when(serviceOfferingRepository.findById(offering.getId())).thenReturn(Optional.of(offering));
        when(serviceEligibilityService.isEligibleForService(nurse, offering)).thenReturn(true);
        when(bookingRepository.existsByNurseProfile_IdAndStartAtAndStatusIn(eq(nurse.getId()), eq(request.getStartAt()), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createDraft(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(BookingErrorCode.BOOKING_SLOT_ALREADY_BOOKED);
        verify(stringRedisTemplate, never()).opsForValue();
    }
}
