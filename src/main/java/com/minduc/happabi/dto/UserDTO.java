package com.minduc.happabi.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserDTO {
    private UUID id;
    private String fullName;
    private String phone;
    private String email;
    private Boolean isActive;
    private List<String> roles;
    private OffsetDateTime createdAt;
}
