package com.knoc.review.service;

import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.review.dto.ReviewRequestCreateRequest;
import com.knoc.review.dto.ReviewRequestCreateResponse;
import com.knoc.review.entity.ReviewRequest;
import com.knoc.review.repository.ReviewRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewRequestService {
    private final OrderRepository orderRepository;
    private final ReviewRequestRepository reviewRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewRequestCreateResponse createReviewRequest(ReviewRequestCreateRequest dto, Long juniorId) {
        // 해당 주문 가져오기
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 주니어 소유 검증
        if (!order.getJunior().getId().equals(juniorId)) {
            throw new BusinessException(ErrorCode.NOT_JUNIOR_FOR_ORDER);
        }

        // 상태 검증
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.REVIEW_REQUEST_NOT_ALLOWED);
        }

        // 중복 검증
        if (reviewRequestRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_REQUEST_ALREADY_EXISTS);
        }

        // 리뷰 요청서 저장
        ReviewRequest reviewRequest = ReviewRequest.builder()
                .order(order)
                .githubPrUrl(dto.getGithubPrUrl())
                .projectContext(dto.getProjectContext())
                .concernPoint(dto.getConcernPoint())
                .build();

        try {
            reviewRequestRepository.saveAndFlush(reviewRequest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REVIEW_REQUEST_ALREADY_EXISTS);
        }


        // 시스템 이벤트 발행
        eventPublisher.publishEvent(new ChatSystemEvent(
                order.getChatRoom().getId(),
                MessageType.REVIEW_SUBMITTED,
                null,
                order.getId()
        ));

        // 6. 저장된 주문을 클라이언트에게 보여줄 전용 응답 객체(DTO)로 변환
        return ReviewRequestCreateResponse.from(reviewRequest);
    }
}
