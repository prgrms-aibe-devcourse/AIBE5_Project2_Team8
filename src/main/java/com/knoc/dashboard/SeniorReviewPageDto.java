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
<<<<<<< HEAD
    // 페이지네이션
    private final int currentPage;
    private final int totalPages;
    private final boolean hasPrevious;
    private final boolean hasNext;
=======
>>>>>>> ec1b409 (feat: 시니어가 자신의 리뷰 목록 조회를 위한 dto,service)
}