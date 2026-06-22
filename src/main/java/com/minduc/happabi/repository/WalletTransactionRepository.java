package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    Optional<WalletTransaction> findByReferenceIdAndStatus(long referenceId, TransactionStatus status);
    boolean existsByReferenceId(long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from WalletTransaction transaction where transaction.referenceId = :referenceId")
    Optional<WalletTransaction> findByReferenceIdForUpdate(@Param("referenceId") long referenceId);
    Optional<List<WalletTransaction>> findTop20ByNurseIdOrderByCreatedAtDesc(UUID nurseId);
    List<WalletTransaction> findByNurseIdAndStatus(UUID nurseId, TransactionStatus status);

    @Query("""
            select coalesce(sum(transaction.walletImpact), 0)
            from WalletTransaction transaction
            where transaction.nurseId = :nurseId
              and transaction.transactionType = :transactionType
              and transaction.status = :status
              and transaction.createdAt >= :startAt
              and transaction.createdAt < :endAt
            """)
    BigDecimal sumWalletImpactByNurseIdAndTransactionTypeAndStatusBetween(@Param("nurseId") UUID nurseId,
                                                                            @Param("transactionType") com.minduc.happabi.enums.TransactionType transactionType,
                                                                            @Param("status") TransactionStatus status,
                                                                            @Param("startAt") Instant startAt,
                                                                            @Param("endAt") Instant endAt);
}
