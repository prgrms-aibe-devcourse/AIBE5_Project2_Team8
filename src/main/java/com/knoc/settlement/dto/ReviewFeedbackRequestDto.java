package com.knoc.settlement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewFeedbackRequestDto {

    @NotNull
    private Long orderId;

    @NotNull
    @Min(1) @Max(5)
    private Byte rating;

    private String comment;
}