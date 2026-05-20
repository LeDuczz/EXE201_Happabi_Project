package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class CccdOcrExtractionResponse {

    private String cccdNumber;
    private String cccdName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate cccdDob;

    private String cccdAddress;
    private Double confidence;
    private Boolean requiresManualReview;
    private List<String> warnings;
}
