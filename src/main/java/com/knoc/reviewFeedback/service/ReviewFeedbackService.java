package com.knoc.reviewFeedback.service;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.reviewFeedback.dto.ReviewPageDto;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.repository.SeniorProfileRepository;
import com.knoc.reviewFeedback.dto.ReviewFeedbackRequestDto;
import com.knoc.reviewFeedback.entity.ReviewFeedback;
import com.knoc.reviewFeedback.repository.ReviewFeedbackRepository;
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
public class ReviewFeedbackService {

    private final ReviewFeedbackRepository reviewFeedbackRepository;
    private final OrderRepository orderRepository;
    private final SeniorProfileRepository seniorProfileRepository;

    @Transactional
    public void createReview(ReviewFeedbackRequestDto dto, Long juniorId) {
        //1. 주문 확인
        Order order = orderRepository.findById(dto.getOrderId()).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // 2. 요청자가 해당 주문의 주니어인지 검증
        if(!order.getJunior().getId().equals(juniorId)){
            throw new BusinessException(ErrorCode.NOT_JUNIOR_FOR_ORDER);
        }
        //3. 결제 완료 상태인지 확인 (결제 완료PAID&&SETTLED 상태에서만 후기 작성이 가능)
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.SETTLED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        //4. 후기를 이미 작성한 주문인지 검증
        if (reviewFeedbackRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        
        //5. 시니어 프로필 확인
        SeniorProfile senior = seniorProfileRepository.findByMemberId(order.getSenior().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

        //6. 검증완료-> 저장
        ReviewFeedback review=ReviewFeedback.builder()
                .order(order)
                .junior(order.getJunior())
                .seniorProfile(senior)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();
        reviewFeedbackRepository.save(review);

        //시니어 프로필 avgReview 최신화
        senior.updateRating(dto.getRating());

    }

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