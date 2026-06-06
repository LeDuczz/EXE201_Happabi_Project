package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.NurseSkill;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseSkillResponse {
    private NurseSkill skill;
    private String label;
    private String groupName;
    private Boolean verified;
    private OffsetDateTime verifiedAt;
}
