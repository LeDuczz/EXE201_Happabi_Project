package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    Optional<WalletTransaction> findByReferenceIdAndStatus(long referenceId, TransactionStatus status);
}
