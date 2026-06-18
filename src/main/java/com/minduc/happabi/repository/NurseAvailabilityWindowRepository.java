package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseAvailabilityWindow;
import com.minduc.happabi.enums.NurseAvailabilityWindowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NurseAvailabilityWindowRepository extends JpaRepository<NurseAvailabilityWindow, UUID> {

    List<NurseAvailabilityWindow> findByNurseProfile_IdOrderByStartAtDesc(UUID nurseProfileId);

    List<NurseAvailabilityWindow> findByNurseProfile_IdAndStatusAndEndAtAfterOrderByStartAtAsc(
            UUID nurseProfileId,
            NurseAvailabilityWindowStatus status,
            OffsetDateTime endAt);

    Optional<NurseAvailabilityWindow> findByIdAndNurseProfile_Id(UUID id, UUID nurseProfileId);

    @Query("""
            select count(window) > 0
            from NurseAvailabilityWindow window
            where window.nurseProfile.id = :nurseProfileId
              and window.status = :status
              and window.startAt <= :startAt
              and window.endAt >= :endAt
            """)
    boolean existsCovering(@Param("nurseProfileId") UUID nurseProfileId,
                           @Param("startAt") OffsetDateTime startAt,
                           @Param("endAt") OffsetDateTime endAt,
                           @Param("status") NurseAvailabilityWindowStatus status);

    @Query("""
            select window
            from NurseAvailabilityWindow window
            where window.nurseProfile.id = :nurseProfileId
              and window.status = :status
              and window.startAt < :endAt
              and window.endAt > :startAt
            order by window.startAt asc
            """)
    List<NurseAvailabilityWindow> findOverlapping(@Param("nurseProfileId") UUID nurseProfileId,
                                                  @Param("startAt") OffsetDateTime startAt,
                                                  @Param("endAt") OffsetDateTime endAt,
                                                  @Param("status") NurseAvailabilityWindowStatus status);
}
