package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewNurseProfileRequest {
    @Size(max = 2000)
    @NotNull
    private String note;
}
