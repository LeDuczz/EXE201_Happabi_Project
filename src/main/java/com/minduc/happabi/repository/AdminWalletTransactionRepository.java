package com.minduc.happabi.repository;

import com.minduc.happabi.entity.AdminWalletTransaction;
import com.minduc.happabi.enums.AdminWalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminWalletTransactionRepository extends JpaRepository<AdminWalletTransaction, UUID> {

    Optional<AdminWalletTransaction> findByBookingIdAndTransactionType(UUID bookingId,
                                                                       AdminWalletTransactionType transactionType);

    Page<AdminWalletTransaction> findByWalletIdOrderByCreatedAtDesc(String walletId, Pageable pageable);

    List<AdminWalletTransaction> findByWalletIdAndTransactionTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            String walletId,
            AdminWalletTransactionType transactionType,
            Instant createdAt);

    @Query("""
            select coalesce(sum(transaction.amount), 0)
            from AdminWalletTransaction transaction
            where transaction.walletId = :walletId
              and transaction.transactionType = :transactionType
              and transaction.createdAt between :startAt and :endAt
            """)
    BigDecimal sumAmountByWalletAndTypeAndCreatedAtBetween(@Param("walletId") String walletId,
                                                           @Param("transactionType") AdminWalletTransactionType transactionType,
                                                           @Param("startAt") Instant startAt,
                                                           @Param("endAt") Instant endAt);
}
