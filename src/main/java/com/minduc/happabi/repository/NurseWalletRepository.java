package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NurseWalletRepository extends JpaRepository<NurseWallet, UUID> {
    Optional<NurseWallet> findById(UUID id);
    Optional<NurseWallet> findByNurseId(UUID nurseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from NurseWallet wallet where wallet.nurseId = :nurseId")
    Optional<NurseWallet> findByNurseIdForUpdate(@Param("nurseId") UUID nurseId);
}
