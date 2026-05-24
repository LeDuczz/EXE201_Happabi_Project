package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NurseWalletRepository extends JpaRepository<NurseWallet, UUID> {
    Optional<NurseWallet> findById(UUID id);
    Optional<NurseWallet> findByNurseId(UUID nurseId);
}
