package com.knoc.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewRequestUpdateRequest(
    @NotNull(message = "orderId는 필수입니다.")
    Long orderId,

    @NotBlank(message = "githubPrUrl은 필수입니다.")
    String githubPrUrl,

    @NotBlank(message = "projectContext는 필수입니다.")
    String projectContext,

    @NotBlank(message = "concernPoint는 필수입니다.")
    String concernPoint
) {}
