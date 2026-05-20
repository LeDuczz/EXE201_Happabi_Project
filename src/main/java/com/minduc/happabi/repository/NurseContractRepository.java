package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseContract;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NurseContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NurseContractRepository extends JpaRepository<NurseContract, UUID> {
    Optional<NurseContract> findTopByNurseOrderByCreatedAtDesc(NurseProfile nurse);
    Optional<NurseContract> findTopByNurseAndStatusOrderByCreatedAtDesc(NurseProfile nurse, NurseContractStatus status);
}
