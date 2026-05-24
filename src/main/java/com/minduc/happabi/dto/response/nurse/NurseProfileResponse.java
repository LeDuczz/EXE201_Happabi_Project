package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseProfileResponse {

    private UUID id;

    private String fullName;

    private String phone;

    private String email;

    private String dayOfBirth;

    private String address;

    private String city;

    private String avatarUrl;
}
