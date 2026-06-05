package com.minduc.happabi.repository;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    boolean existsByNurseProfile_IdAndStartAtAndStatusIn(UUID nurseProfileId,
                                                         OffsetDateTime startAt,
                                                         Collection<BookingStatus> statuses);
}
