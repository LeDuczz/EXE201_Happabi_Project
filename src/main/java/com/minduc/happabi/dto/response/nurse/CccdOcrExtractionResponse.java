package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
