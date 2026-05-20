package com.minduc.happabi.dto.response.mother;

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
