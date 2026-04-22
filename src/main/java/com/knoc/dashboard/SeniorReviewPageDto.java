package com.knoc.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SeniorReviewPageDto {
    private final String nickname;
    private final BigDecimal averageRating;
    private final int reviewCount;
    private final List<SeniorDashBoardDto.ReviewSummeryDto> reviews;
}