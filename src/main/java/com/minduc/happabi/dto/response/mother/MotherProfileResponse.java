package com.minduc.happabi.dto.response.mother;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MotherProfileResponse {

    private UUID id;

    private String fullName;

    private String phone;

    private String email;

    private LocalDate babyBirthDate;

    private LocalDate dayOfBirth;

    private String address;

    private String city;

    private String avatarUrl;
}
