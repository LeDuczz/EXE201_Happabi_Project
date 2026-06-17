package com.minduc.happabi.repository;

import com.minduc.happabi.entity.BookingSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, UUID> {

    @Modifying
    @Query(value = """
            insert into booking_slots (slot_id, nurse_profile_id, start_at, slot_status, created_at, updated_at)
            values (:slotId, :nurseProfileId, :startAt, 'AVAILABLE', current_timestamp, current_timestamp)
            on conflict (nurse_profile_id, start_at) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("slotId") UUID slotId,
                       @Param("nurseProfileId") UUID nurseProfileId,
                       @Param("startAt") OffsetDateTime startAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot
            from BookingSlot slot
            join fetch slot.nurseProfile nurse
            where nurse.id = :nurseProfileId
              and slot.startAt = :startAt
            """)
    Optional<BookingSlot> findByNurseProfileIdAndStartAtForUpdate(@Param("nurseProfileId") UUID nurseProfileId,
                                                                  @Param("startAt") OffsetDateTime startAt);
}
