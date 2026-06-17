package com.minduc.happabi.repository;

import com.minduc.happabi.entity.BookingPaymentTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BookingPaymentTransactionRepository extends JpaRepository<BookingPaymentTransaction, UUID> {

    boolean existsByTransactionId(Long transactionId);

    Optional<BookingPaymentTransaction> findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(UUID bookingId,
                                                                                          TransactionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select transaction
            from BookingPaymentTransaction transaction
            join fetch transaction.booking booking
            join fetch booking.mother mother
            join fetch booking.nurseProfile nurseProfile
            join fetch booking.serviceOffering serviceOffering
            where transaction.transactionId = :transactionId
            """)
    Optional<BookingPaymentTransaction> findByTransactionIdForUpdate(@Param("transactionId") Long transactionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookingPaymentTransaction transaction
            set transaction.status = :nextStatus,
                transaction.paidAt = :paidAt,
                transaction.description = :description,
                transaction.updatedAt = :paidAt
            where transaction.transactionId = :transactionId
              and transaction.status = :currentStatus
            """)
    int markStatusIfPending(@Param("transactionId") Long transactionId,
                            @Param("currentStatus") TransactionStatus currentStatus,
                            @Param("nextStatus") TransactionStatus nextStatus,
                            @Param("paidAt") OffsetDateTime paidAt,
                            @Param("description") String description);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookingPaymentTransaction transaction
            set transaction.status = :nextStatus,
                transaction.description = :description,
                transaction.updatedAt = :updatedAt
            where transaction.booking.id = :bookingId
              and transaction.status = :currentStatus
            """)
    int markBookingTransactionsStatus(@Param("bookingId") UUID bookingId,
                                      @Param("currentStatus") TransactionStatus currentStatus,
                                      @Param("nextStatus") TransactionStatus nextStatus,
                                      @Param("description") String description,
                                      @Param("updatedAt") OffsetDateTime updatedAt);
}
