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

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    Optional<WalletTransaction> findByReferenceIdAndStatus(long referenceId, TransactionStatus status);
    boolean existsByReferenceId(long referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from WalletTransaction transaction where transaction.referenceId = :referenceId")
    Optional<WalletTransaction> findByReferenceIdForUpdate(@Param("referenceId") long referenceId);
    Optional<List<WalletTransaction>> findTop20ByNurseIdOrderByCreatedAtDesc(UUID nurseId);
    List<WalletTransaction> findByNurseIdAndStatus(UUID nurseId, TransactionStatus status);
}
