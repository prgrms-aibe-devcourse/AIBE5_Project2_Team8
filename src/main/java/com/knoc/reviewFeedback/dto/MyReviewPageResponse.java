package com.knoc.reviewFeedback.dto;

import lombok.Builder;

import java.util.List;

public record MyReviewPageResponse(List<ReviewPageDto.ReviewCardDto> myReviews, int totalCount) {
    @Builder
    public MyReviewPageResponse {
    }
}
