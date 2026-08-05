package com.minduc.happabi.service.admin.impl;

import com.minduc.happabi.dto.request.admin.AdminBookingSearchCriteria;
import com.minduc.happabi.dto.response.admin.AdminBookingResponse;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.ServiceOffering;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.BookingErrorCode;
import com.minduc.happabi.repository.BookingRepository;
import com.minduc.happabi.service.admin.IAdminBookingService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBookingServiceImpl implements IAdminBookingService {

    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminBookingResponse> getBookings(AdminBookingSearchCriteria criteria, Pageable pageable) {
        return bookingRepository.findAll(
                        buildSpecification(criteria),
                        pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBookingResponse getBookingDetail(UUID bookingId) {
        Booking booking = bookingRepository.findByIdWithPaymentRelations(bookingId)
                .orElseThrow(() -> new AppException(BookingErrorCode.BOOKING_NOT_FOUND));
        return toResponse(booking);
    }

    Specification<Booking> buildSpecification(AdminBookingSearchCriteria criteria) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (criteriaQuery != null && !Long.class.equals(criteriaQuery.getResultType())) {
                root.fetch("mother", JoinType.LEFT);
                var nurseFetch = root.fetch("nurseProfile", JoinType.LEFT);
                nurseFetch.fetch("user", JoinType.LEFT);
                root.fetch("serviceOffering", JoinType.LEFT);
            }
            if (criteriaQuery != null) {
                criteriaQuery.distinct(true);
            }

            Join<Booking, User> mother = root.join("mother", JoinType.LEFT);
            Join<Booking, NurseProfile> nurseProfile = root.join("nurseProfile", JoinType.LEFT);
            Join<NurseProfile, User> nurseUser = nurseProfile.join("user", JoinType.LEFT);
            Join<Booking, ServiceOffering> service = root.join("serviceOffering", JoinType.LEFT);

            var predicates = new ArrayList<Predicate>();
            String normalizedQuery = normalize(criteria.getQuery());
            if (normalizedQuery != null) {
                String likeQuery = "%" + normalizedQuery + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("bookingKey")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(mother.get("fullName")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(mother.get("phone")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(mother.get("email")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(nurseUser.get("fullName")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(nurseUser.get("phone")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(nurseUser.get("email")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(service.get("serviceName")), likeQuery),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("serviceAddress")), likeQuery)
                ));
            }

            if (criteria.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), criteria.getStatus()));
            }
            if (criteria.getPaymentOption() != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentOption"), criteria.getPaymentOption()));
            }
            addRange(predicates, criteriaBuilder, root.get("createdAt"), criteria.getCreatedFrom(), criteria.getCreatedTo());
            addRange(predicates, criteriaBuilder, root.get("startAt"), criteria.getServiceFrom(), criteria.getServiceTo());

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addRange(
            ArrayList<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            jakarta.persistence.criteria.Path<OffsetDateTime> path,
            OffsetDateTime from,
            OffsetDateTime to) {
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(path, from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThan(path, to));
        }
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private AdminBookingResponse toResponse(Booking booking) {
        User mother = booking.getMother();
        NurseProfile nurseProfile = booking.getNurseProfile();
        User nurseUser = nurseProfile == null ? null : nurseProfile.getUser();
        ServiceOffering service = booking.getServiceOffering();

        return AdminBookingResponse.builder()
                .id(booking.getId())
                .bookingKey(booking.getBookingKey())
                .status(booking.getStatus())
                .paymentOption(booking.getPaymentOption())
                .startAt(booking.getStartAt())
                .endAt(booking.getEndAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .grossAmount(booking.getGrossAmount())
                .appPaymentAmount(booking.getAppPaymentAmount())
                .depositAmount(booking.getDepositAmount())
                .remainingCashAmount(booking.getRemainingCashAmount())
                .platformFeeAmount(booking.getPlatformFeeAmount())
                .nurseEarningAmount(booking.getNurseEarningAmount())
                .serviceAddress(booking.getServiceAddress())
                .motherNote(booking.getMotherNote())
                .mother(toParty(mother))
                .nurse(toParty(nurseUser))
                .service(toServiceSummary(service))
                .build();
    }

    private AdminBookingResponse.Party toParty(User user) {
        if (user == null) {
            return null;
        }
        return AdminBookingResponse.Party.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .build();
    }

    private AdminBookingResponse.ServiceSummary toServiceSummary(ServiceOffering service) {
        if (service == null) {
            return null;
        }
        return AdminBookingResponse.ServiceSummary.builder()
                .id(service.getId())
                .serviceCode(service.getServiceCode())
                .serviceName(service.getServiceName())
                .groupName(service.getGroupName())
                .build();
    }
}
