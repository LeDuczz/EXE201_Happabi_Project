package com.minduc.happabi.dto.openai;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OpenAiCccdOcrResult {

    private String cccdNumber;
    private String cccdName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate cccdDob;

    private String cccdAddress;
    private Double confidence;
    private Boolean requiresManualReview;
    private List<String> warnings = new ArrayList<>();
}
