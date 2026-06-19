package com.minduc.happabi.dto.request.mother;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class NurseAiComparisonRequest {

    @NotEmpty(message = "At least two nurse profiles are required.")
    @Size(min = 2, max = 4, message = "You can compare from 2 to 4 nurse profiles.")
    private List<@NotNull UUID> nurseProfileIds;

    @Size(max = 1000, message = "Care need must be at most 1000 characters.")
    private String careNeed;

    @Size(max = 500, message = "Preference must be at most 500 characters.")
    private String preference;
}
