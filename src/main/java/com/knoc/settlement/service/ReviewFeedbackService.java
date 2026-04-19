package com.knoc.settlement.service;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.repository.SeniorProfileRepository;
import com.knoc.settlement.dto.ReviewFeedbackRequestDto;
import com.knoc.settlement.entity.ReviewFeedback;
import com.knoc.settlement.repository.ReviewFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewFeedbackService {

    private final ReviewFeedbackRepository reviewFeedbackRepository;
    private final OrderRepository orderRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void createReview(ReviewFeedbackRequestDto dto, Long juniorId) {
        // 1. 주문 조회
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 2. 요청자가 해당 주문의 주니어인지 검증
        if (!order.getJunior().getId().equals(juniorId)) {
            throw new BusinessException(ErrorCode.NOT_JUNIOR_FOR_ORDER);
        }

        // 3. 결제 완료 상태인지 검증 (PAID 상태에서만 후기 작성 가능)
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }

        // 4. 동일 주문에 대한 중복 후기 방지
        if (reviewFeedbackRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 5. 주니어, 시니어 프로필 조회
        Member junior = memberRepository.findById(juniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(order.getSenior().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

        // 6. 후기 저장
        ReviewFeedback feedback = ReviewFeedback.builder()
                .order(order)
                .junior(junior)
                .seniorProfile(seniorProfile)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();

        reviewFeedbackRepository.save(feedback);

        // 7. 시니어 평점 업데이트
        seniorProfile.updateRating(dto.getRating());
    }
}