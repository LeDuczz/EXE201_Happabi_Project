package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseKyc;
import com.minduc.happabi.entity.NurseProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NurseKycRepository extends JpaRepository<NurseKyc, UUID> {
    Optional<NurseKyc> findByNurse(NurseProfile nurse);

    List<NurseKyc> findTop50ByCccdImagesDeletedAtIsNullAndCccdImagesDeleteAfterLessThanEqual(
            OffsetDateTime now);
}
