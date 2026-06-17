package com.minduc.happabi.repository;

import com.minduc.happabi.entity.BookingSettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookingSettlementRepository extends JpaRepository<BookingSettlement, UUID> {

    boolean existsByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settlement from BookingSettlement settlement where settlement.bookingId = :bookingId")
    Optional<BookingSettlement> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
}
