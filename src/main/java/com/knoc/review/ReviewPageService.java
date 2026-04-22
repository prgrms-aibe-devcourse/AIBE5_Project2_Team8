package com.knoc.review;

import com.knoc.review.dto.ReviewPageDto;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.settlement.entity.ReviewFeedback;
import com.knoc.settlement.repository.ReviewFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewPageService {

    private final ReviewFeedbackRepository reviewFeedbackRepository;

    public ReviewPageDto getReviewPage() {
        List<ReviewFeedback> feedbacks = reviewFeedbackRepository.findAllByOrderByCreatedAtDesc();

        List<ReviewPageDto.ReviewCardDto> reviewCards = mapToCards(feedbacks);
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<ReviewPageDto.TopSeniorDto> topSeniors = mapToTopSeniors(
                reviewFeedbackRepository.findTop3ActiveSeniorsThisMonth(startOfMonth, PageRequest.of(0, 3)));

        return ReviewPageDto.builder()
                .reviews(reviewCards)
                .totalCount(feedbacks.size())
                .topSeniors(topSeniors)
                .build();
    }

    private List<ReviewPageDto.ReviewCardDto> mapToCards(List<ReviewFeedback> feedbacks) {
        return feedbacks.stream().map(r -> ReviewPageDto.ReviewCardDto.builder()
                .seniorProfileId(r.getSeniorProfile().getId())
                .juniorName(r.getJunior().getNickname())
                .seniorName(r.getSeniorProfile().getMember().getNickname())
                .mentoringType(r.getSeniorProfile().getPosition())
                .timeAgo(timeAgo(r.getCreatedAt()))
                .rating(r.getRating())
                .content(r.getComment())
                .build()
        ).toList();
    }

    private List<ReviewPageDto.TopSeniorDto> mapToTopSeniors(List<SeniorProfile> seniors) {
        return seniors.stream().map(s -> ReviewPageDto.TopSeniorDto.builder()
                .seniorProfileId(s.getId())
                .name(s.getMember().getNickname())
                .rating(s.getAvgRating())
                .build()
        ).toList();
    }

    private String timeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return "";
        long days = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
        if (days == 0) return "오늘";
        if (days < 7) return days + "일 전";
        if (days < 30) return (days / 7) + "주 전";
        return (days / 30) + "개월 전";
    }
}
