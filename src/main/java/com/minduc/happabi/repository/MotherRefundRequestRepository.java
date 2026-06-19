package com.minduc.happabi.repository;

import com.minduc.happabi.entity.MotherRefundRequest;
import com.minduc.happabi.enums.MotherRefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MotherRefundRequestRepository extends JpaRepository<MotherRefundRequest, UUID> {

    Optional<MotherRefundRequest> findByBooking_Id(UUID bookingId);

    Page<MotherRefundRequest> findByMother_IdOrderByCreatedAtDesc(UUID motherId, Pageable pageable);

    Page<MotherRefundRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<MotherRefundRequest> findByStatusOrderByCreatedAtDesc(MotherRefundStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refund
            from MotherRefundRequest refund
            join fetch refund.booking booking
            join fetch refund.mother mother
            where refund.id = :id
            """)
    Optional<MotherRefundRequest> findByIdForUpdate(@Param("id") UUID id);
}
