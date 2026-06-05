package com.minduc.happabi.dto.request.user;

import com.minduc.happabi.enums.AvailabilityStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateNurseProfileDisplayRequest {

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;

    @Size(max = 200, message = "Service area must not exceed 200 characters")
    private String serviceArea;

    private AvailabilityStatus availabilityStatus;
}
