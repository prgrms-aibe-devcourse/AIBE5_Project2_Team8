package com.knoc.review.dto;

import com.knoc.review.entity.ReviewRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewRequestCreateResponse {
    private Long orderId;

    public static ReviewRequestCreateResponse from(ReviewRequest reviewRequest) {
        return ReviewRequestCreateResponse.builder()
                .orderId(reviewRequest.getOrder().getId())
                .build();
    }
}
