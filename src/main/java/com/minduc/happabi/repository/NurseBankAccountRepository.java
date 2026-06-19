package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseBankAccount;
import com.minduc.happabi.enums.NurseBankAccountStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NurseBankAccountRepository extends JpaRepository<NurseBankAccount, UUID> {

    @EntityGraph(attributePaths = {"nurseProfile", "nurseProfile.user"})
    Optional<NurseBankAccount> findByNurseProfile_Id(UUID nurseProfileId);

    @EntityGraph(attributePaths = {"nurseProfile", "nurseProfile.user"})
    Optional<NurseBankAccount> findByNurseProfile_IdAndStatus(UUID nurseProfileId, NurseBankAccountStatus status);
}
