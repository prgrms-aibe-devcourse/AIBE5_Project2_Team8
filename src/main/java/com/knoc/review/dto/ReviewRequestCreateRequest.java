package com.knoc.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReviewRequestCreateRequest {
    @NotNull
    private final Long orderId;
    @NotBlank
    private final String githubPrUrl;
    @NotBlank
    private final String projectContext;
    @NotBlank
    private final String concernPoint;
}
