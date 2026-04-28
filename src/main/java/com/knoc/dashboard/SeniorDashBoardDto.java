package com.knoc.dashboard;

import com.knoc.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
public class SeniorDashBoardDto {
    private final Long id;
    private final String nickname;
    private final String profileImageUrl;
    private final long pendingCount;
    private final long inProgressCount;
    private final long completedCount;
    // 프로필 (senior.html에서 직접 접근)
    private final String company;
    private final int careerYears;
    private final String linkedinUrl;
    private final List<String> skills;
    // 평점
    private final BigDecimal averageRating;
    private final int reviewCount;
    private final Map<Integer, Integer> ratingDistribution;
    // 목록
    private final List<OrderSummaryDto> orders;
    private final List<ReviewSummeryDto> reviews;
    // AI 인사이트 (미구현 → null)
    private final List<String> aiInsights;

    @Builder
    public SeniorDashBoardDto(Long id, String nickname, String profileImageUrl, long pendingCount, long inProgressCount, long completedCount, String company, int careerYears, String linkedinUrl, List<String> skills, BigDecimal averageRating, int reviewCount, Map<Integer, Integer> ratingDistribution, List<OrderSummaryDto> orders, List<ReviewSummeryDto> reviews, List<String> aiInsights) {
        this.id = id;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.pendingCount = pendingCount;
        this.inProgressCount = inProgressCount;
        this.completedCount = completedCount;
        this.company = company;
        this.careerYears = careerYears;
        this.linkedinUrl = linkedinUrl;
        this.skills = skills;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.ratingDistribution = ratingDistribution;
        this.orders = orders;
        this.reviews = reviews;
        this.aiInsights = aiInsights;
    }


    @Getter
    public static class OrderSummaryDto {
        private final Long orderId;
        private final String juniorNickname;
        private final OrderStatus status;
        private final boolean hasReview;

        @Builder
        public OrderSummaryDto(Long orderId, String juniorNickname,
                               OrderStatus status, boolean hasReview) {
            this.orderId = orderId;
            this.juniorNickname = juniorNickname;
            this.status = status;
            this.hasReview = hasReview;
        }
    }

    @Getter
    public static class ReviewSummeryDto {
        private final Long reviewId;
        private final String reviewerNickname;
        private final int rating;
        private final String comment;
        private final LocalDateTime createdAt;

        @Builder
        public ReviewSummeryDto(Long reviewId, String reviewerNickname, int rating,
                                String comment, LocalDateTime createdAt) {
            this.reviewId = reviewId;
            this.reviewerNickname = reviewerNickname;
            this.rating = rating;
            this.comment = comment;
            this.createdAt = createdAt;
        }
    }
}