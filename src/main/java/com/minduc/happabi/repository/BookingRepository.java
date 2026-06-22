package com.minduc.happabi.repository;

import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    boolean existsByNurseProfile_IdAndStartAtAndStatusIn(UUID nurseProfileId,
                                                         OffsetDateTime startAt,
                                                         Collection<BookingStatus> statuses);

    long countByStatus(BookingStatus status);

    long countByStatusAndCreatedAtBetween(BookingStatus status, OffsetDateTime startAt, OffsetDateTime endAt);

    long countByStatusAndUpdatedAtBetween(BookingStatus status, OffsetDateTime startAt, OffsetDateTime endAt);

    long countByMother_IdAndStatusIn(UUID motherId, Collection<BookingStatus> statuses);

    long countByStartAtBetween(OffsetDateTime startAt, OffsetDateTime endAt);

    @Query("""
            select coalesce(sum(booking.appPaymentAmount), 0)
            from Booking booking
            where booking.status in :statuses
              and booking.createdAt between :startAt and :endAt
            """)
    Long sumAppPaymentAmountByStatusInAndCreatedAtBetween(@Param("statuses") Collection<BookingStatus> statuses,
                                                          @Param("startAt") OffsetDateTime startAt,
                                                          @Param("endAt") OffsetDateTime endAt);

    List<Booking> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(OffsetDateTime startAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mother mother
            join fetch booking.nurseProfile nurseProfile
            join fetch booking.serviceOffering serviceOffering
            where booking.id = :bookingId
              and mother.id = :motherId
            """)
    Optional<Booking> findByIdAndMotherIdForUpdate(@Param("bookingId") UUID bookingId,
                                                   @Param("motherId") UUID motherId);

    @Query("""
            select booking
            from Booking booking
            join fetch booking.mother mother
            join fetch booking.nurseProfile nurseProfile
            join fetch nurseProfile.user nurseUser
            join fetch booking.serviceOffering serviceOffering
            where booking.id = :bookingId
            """)
    Optional<Booking> findByIdWithPaymentRelations(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mother mother
            join fetch booking.nurseProfile nurseProfile
            join fetch nurseProfile.user nurseUser
            join fetch booking.serviceOffering serviceOffering
            join fetch booking.slot slot
            where booking.id = :bookingId
            """)
    Optional<Booking> findByIdForCancellationUpdate(@Param("bookingId") UUID bookingId);

    @Query("""
            select booking
            from Booking booking
            join fetch booking.mother mother
            join fetch booking.nurseProfile nurseProfile
            join fetch nurseProfile.user nurseUser
            join fetch booking.serviceOffering serviceOffering
            join fetch booking.slot slot
            where mother.id = :motherId
              and booking.status = :status
              and booking.paymentExpiresAt > :now
            order by booking.paymentExpiresAt asc
            """)
    List<Booking> findPendingPaymentsByMotherId(@Param("motherId") UUID motherId,
                                                @Param("status") BookingStatus status,
                                                @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking booking
            set booking.status = :nextStatus,
                booking.updatedAt = :updatedAt
            where booking.id = :bookingId
              and booking.status = :currentStatus
              and booking.paymentExpiresAt > :paidAt
            """)
    int markPaidIfPendingAndNotExpired(@Param("bookingId") UUID bookingId,
                                       @Param("currentStatus") BookingStatus currentStatus,
                                       @Param("nextStatus") BookingStatus nextStatus,
                                       @Param("paidAt") OffsetDateTime paidAt,
                                       @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            select booking.id
            from Booking booking
            where booking.status = :status
              and booking.paymentExpiresAt <= :now
            order by booking.paymentExpiresAt asc
            """)
    List<UUID> findExpiredPendingPaymentIds(@Param("status") BookingStatus status,
                                            @Param("now") OffsetDateTime now,
                                            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking booking
            set booking.status = :nextStatus,
                booking.updatedAt = :updatedAt
            where booking.id = :bookingId
              and booking.status = :currentStatus
              and booking.paymentExpiresAt <= :now
            """)
    int cancelExpiredPendingPayment(@Param("bookingId") UUID bookingId,
                                    @Param("currentStatus") BookingStatus currentStatus,
                                    @Param("nextStatus") BookingStatus nextStatus,
                                    @Param("now") OffsetDateTime now,
                                    @Param("updatedAt") OffsetDateTime updatedAt);
}
