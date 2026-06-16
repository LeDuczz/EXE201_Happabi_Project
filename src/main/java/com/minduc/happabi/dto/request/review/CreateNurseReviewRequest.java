package com.minduc.happabi.dto.request.review;

import com.minduc.happabi.enums.NurseReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateNurseReviewRequest {

    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 1000)
    private String comment;

    @Size(max = 7)
    private List<NurseReviewTag> tags;
}
