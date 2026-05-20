package com.minduc.happabi.dto.request.nurse;

import com.minduc.happabi.enums.NurseSpecialty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateNurseProfileRequest {
    @Size(max = 100)
    private String licenseNumber;
    private LocalDate dateOfBirth;
    private NurseSpecialty specialty;
    @Min(0)
    @Max(60)
    private Integer experienceYears;
    @Size(max = 2000)
    private String bio;
    @Size(max = 200)
    private String serviceArea;
    @Size(max = 200)
    private String address;
    @Size(max = 50)
    private String city;
}
