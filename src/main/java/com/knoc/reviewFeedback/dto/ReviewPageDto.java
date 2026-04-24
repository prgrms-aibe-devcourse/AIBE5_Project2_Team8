package com.knoc.reviewFeedback.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ReviewPageDto {
    private final List<ReviewCardDto> reviews;
    private final long totalCount;
    private final List<TopSeniorDto> topSeniors;

    @Getter
    @Builder
    public static class ReviewCardDto {
        private final Long seniorProfileId;
        private final String juniorName;
        private final String seniorName;
        private final String mentoringType;
        private final String timeAgo;
        private final byte rating;
        private final String content;
    }

    @Getter
    @Builder
    public static class TopSeniorDto {
        private final Long seniorProfileId;
        private final String name;
        private final BigDecimal rating;
    }
}