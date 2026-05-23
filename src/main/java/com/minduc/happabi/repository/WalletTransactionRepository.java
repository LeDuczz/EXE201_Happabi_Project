package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    Optional<WalletTransaction> findByIdAndStatus(String orderCode, TransactionStatus status);
}
