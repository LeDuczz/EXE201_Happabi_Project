package com.minduc.happabi.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseProfileResponse {

    private UUID id;

    private String fullName;

    private String phone;

    private String email;

    private String dayOfBirth;

    private String avatarUrl;
}
