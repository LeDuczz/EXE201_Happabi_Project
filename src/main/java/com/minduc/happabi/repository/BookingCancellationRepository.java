package com.minduc.happabi.repository;

import com.minduc.happabi.entity.BookingCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingCancellationRepository extends JpaRepository<BookingCancellation, UUID> {
    boolean existsByBooking_Id(UUID bookingId);

    Optional<BookingCancellation> findByBooking_Id(UUID bookingId);
}
