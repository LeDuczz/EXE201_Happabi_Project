package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseSkillEntity;
import com.minduc.happabi.enums.NurseSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NurseSkillRepository extends JpaRepository<NurseSkillEntity, UUID> {
    List<NurseSkillEntity> findByNurseOrderBySkillAsc(NurseProfile nurse);

    List<NurseSkillEntity> findByNurseAndVerifiedAtIsNotNullOrderBySkillAsc(NurseProfile nurse);

    List<NurseSkillEntity> findByNurseAndSkillIn(NurseProfile nurse, Collection<NurseSkill> skills);

    Optional<NurseSkillEntity> findByNurseAndSkill(NurseProfile nurse, NurseSkill skill);

    void deleteByNurseAndSkillNotIn(NurseProfile nurse, Collection<NurseSkill> skills);
}
