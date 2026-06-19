package com.minduc.happabi.service.mother.impl;

import com.minduc.happabi.dto.response.nurse.NursePublicProfileResponse;
import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseAvailabilityWindow;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseAvailabilityWindowStatus;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.mapper.NursePublicProfileMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseCertificationRepository;
import com.minduc.happabi.repository.NurseAvailabilityWindowRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.service.booking.IServiceEligibilityService;
import com.minduc.happabi.service.mother.IMotherNurseProfileService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MotherNurseProfileServiceImpl implements IMotherNurseProfileService {

    private final NurseProfileRepository nurseProfileRepository;
    private final NurseCertificationRepository certificationRepository;
    private final NurseAvailabilityWindowRepository availabilityWindowRepository;
    private final NursePublicProfileMapper nursePublicProfileMapper;
    private final IS3Service s3Service;
    private final IServiceEligibilityService serviceEligibilityService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    @TimedAction("SEARCH_ACTIVE_NURSES")
    @LogExecution
    public Page<NursePublicProfileResponse> searchActiveNurses(String keyword,
                                                               String city,
                                                               NurseSpecialty specialty,
                                                               LocalDate availableDate,
                                                               Boolean availableOnly,
                                                               Pageable pageable) {
        AvailabilityStatus availabilityStatus = Boolean.TRUE.equals(availableOnly) && availableDate == null
                ? AvailabilityStatus.AVAILABLE
                : null;
        AvailabilitySearchRange range = availabilitySearchRange(availableDate);

        return nurseProfileRepository.findAll(
                        publicProfileSpec(availabilityStatus, specialty, normalize(city), normalize(keyword), range),
                        pageable)
                .map(profile -> toPublicResponse(profile, false, range));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MOTHER')")
    @LogExecution
    @TimedAction("GET_ACTIVE_NURSE_PROFILE")
    public NursePublicProfileResponse getActiveNurse(UUID profileId) {
        NurseProfile profile = nurseProfileRepository.findByIdAndNurseStatus(profileId, NurseStatus.ACTIVE)
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PUBLIC_PROFILE_NOT_FOUND));
        return toPublicResponse(profile, true, availabilitySearchRange(null));
    }

    private NursePublicProfileResponse toPublicResponse(NurseProfile profile,
                                                        boolean includeCertifications,
                                                        AvailabilitySearchRange range) {
        List<NurseCertification> certifications = includeCertifications
                ? certificationRepository.findByNurseAndIsVerifiedTrueOrderByIdDesc(profile)
                : List.of();
        String avatarUrl = s3Service.presign(profile.getUser().getAvatarS3Key());
        NursePublicProfileResponse response = nursePublicProfileMapper.toResponse(
                profile,
                certifications,
                avatarUrl,
                serviceEligibilityService.getNurseSkills(profile, true),
                serviceEligibilityService.getEligibleServices(profile, null));
        NurseAvailabilityWindow window = firstMatchingWindow(profile, range);
        if (window == null) {
            return response;
        }
        return response.toBuilder()
                .availabilityWindowStartAt(window.getStartAt())
                .availabilityWindowEndAt(window.getEndAt())
                .build();
    }

    private Specification<NurseProfile> publicProfileSpec(AvailabilityStatus availabilityStatus,
                                                          NurseSpecialty specialty,
                                                          String city,
                                                          String keyword,
                                                          AvailabilitySearchRange range) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.INNER);
            }
            Join<Object, Object> user = root.join("user", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("nurseStatus"), NurseStatus.ACTIVE));
            predicates.add(cb.or(
                    cb.isNull(root.get("bookingSuspendedUntil")),
                    cb.lessThanOrEqualTo(root.get("bookingSuspendedUntil"), OffsetDateTime.now())
            ));
            predicates.add(hasAvailabilityWindow(root, query.subquery(Long.class), cb, range));

            if (availabilityStatus != null) {
                predicates.add(cb.equal(root.get("availabilityStatus"), availabilityStatus));
            }
            if (specialty != null) {
                predicates.add(cb.equal(root.get("specialty"), specialty));
            }
            if (city != null) {
                predicates.add(cb.like(cb.lower(root.get("city")), likePattern(city)));
            }
            if (keyword != null) {
                String pattern = likePattern(keyword);
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("bio")), pattern),
                        cb.like(cb.lower(root.get("serviceArea")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate hasAvailabilityWindow(Root<NurseProfile> root,
                                            Subquery<Long> subquery,
                                            jakarta.persistence.criteria.CriteriaBuilder cb,
                                            AvailabilitySearchRange range) {
        Root<NurseAvailabilityWindow> window = subquery.from(NurseAvailabilityWindow.class);
        subquery.select(cb.literal(1L));
        subquery.where(
                cb.equal(window.get("nurseProfile").get("id"), root.get("id")),
                cb.equal(window.get("status"), NurseAvailabilityWindowStatus.ACTIVE),
                cb.lessThan(window.get("startAt"), range.endAt()),
                cb.greaterThan(window.get("endAt"), range.startAt())
        );
        return cb.exists(subquery);
    }

    private NurseAvailabilityWindow firstMatchingWindow(NurseProfile profile, AvailabilitySearchRange range) {
        return availabilityWindowRepository.findOverlapping(
                        profile.getId(),
                        range.startAt(),
                        range.endAt(),
                        NurseAvailabilityWindowStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private AvailabilitySearchRange availabilitySearchRange(LocalDate availableDate) {
        if (availableDate == null) {
            OffsetDateTime now = OffsetDateTime.now();
            return new AvailabilitySearchRange(now, now.plusSeconds(1));
        }
        OffsetDateTime startAt = availableDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        return new AvailabilitySearchRange(startAt, startAt.plusDays(1));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String likePattern(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    private record AvailabilitySearchRange(OffsetDateTime startAt, OffsetDateTime endAt) {
    }
}
