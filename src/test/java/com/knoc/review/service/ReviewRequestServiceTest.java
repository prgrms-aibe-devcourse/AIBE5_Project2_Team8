package com.knoc.review.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.review.dto.ReviewRequestCreateRequest;
import com.knoc.review.dto.ReviewRequestCreateResponse;
import com.knoc.review.entity.ReviewRequest;
import com.knoc.review.repository.ReviewRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewRequestServiceTest {

    @InjectMocks
    private ReviewRequestService reviewRequestService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReviewRequestRepository reviewRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("리뷰 요청서 작성 성공: PAID 주문에 대해 리뷰 요청서를 저장하고 REVIEW_SUBMITTED 이벤트를 발행한다.")
    void createReviewRequest_Success() {
        // given
        Long juniorId = 1L;
        Long orderId = 10L;
        Long chatRoomId = 3L;

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                orderId,
                "https://github.com/user/repo/pull/1",
                "프로젝트 배경 설명",
                "질문/고민 포인트"
        );

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(chatRoomId);

        Order order = Order.builder()
                .orderNumber("ORD-TEST-KEY")
                .chatRoom(chatRoom)
                .junior(junior)
                .senior(mock(Member.class))
                .amount(15000)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        // 결제 완료 상태로 전환
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewRequestRepository.existsByOrderId(orderId)).willReturn(false);
        given(reviewRequestRepository.saveAndFlush(any(ReviewRequest.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ReviewRequestCreateResponse response = reviewRequestService.createReviewRequest(dto, juniorId);

        // then
        assertThat(response.getOrderId()).isEqualTo(orderId);

        verify(reviewRequestRepository, times(1)).saveAndFlush(any(ReviewRequest.class));

        ArgumentCaptor<ChatSystemEvent> captor = ArgumentCaptor.forClass(ChatSystemEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        ChatSystemEvent event = captor.getValue();
        assertThat(event.roomId()).isEqualTo(chatRoomId);
        assertThat(event.type()).isEqualTo(MessageType.REVIEW_SUBMITTED);
        assertThat(event.referenceId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 주문이 없으면 ORDER_NOT_FOUND 예외가 발생한다.")
    void createReviewRequest_Fail_OrderNotFound() {
        // given
        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                999L,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 요청자가 해당 주문의 주니어가 아니면 NOT_JUNIOR_FOR_ORDER 예외가 발생한다.")
    void createReviewRequest_Fail_NotJuniorForOrder() {
        // given
        Long orderId = 10L;

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                orderId,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );

        Member orderJunior = mock(Member.class);
        given(orderJunior.getId()).willReturn(1L);

        Order order = Order.builder()
                .orderNumber("ORD-TEST-KEY")
                .chatRoom(mock(ChatRoom.class))
                .junior(orderJunior)
                .senior(mock(Member.class))
                .amount(15000)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_JUNIOR_FOR_ORDER.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 주문 상태가 PAID가 아니면 REVIEW_REQUEST_NOT_ALLOWED 예외가 발생한다.")
    void createReviewRequest_Fail_OrderNotPaid() {
        // given
        Long juniorId = 1L;
        Long orderId = 10L;

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                orderId,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("ORD-TEST-KEY")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(15000)
                .build(); // 기본 PENDING
        ReflectionTestUtils.setField(order, "id", orderId);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, juniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_REQUEST_NOT_ALLOWED.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 이미 리뷰 요청서가 존재하면 REVIEW_REQUEST_ALREADY_EXISTS 예외가 발생한다.")
    void createReviewRequest_Fail_AlreadyExists() {
        // given
        Long juniorId = 1L;
        Long orderId = 10L;

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                orderId,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("ORD-TEST-KEY")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(15000)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewRequestRepository.existsByOrderId(orderId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, juniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_REQUEST_ALREADY_EXISTS.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 저장 중 UNIQUE 제약 위반이면 REVIEW_REQUEST_ALREADY_EXISTS 예외로 변환한다.")
    void createReviewRequest_Fail_DataIntegrityViolation() {
        // given
        Long juniorId = 1L;
        Long orderId = 10L;

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                orderId,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("ORD-TEST-KEY")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(15000)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewRequestRepository.existsByOrderId(orderId)).willReturn(false);
        given(reviewRequestRepository.saveAndFlush(any(ReviewRequest.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint"));

        // when & then
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, juniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_REQUEST_ALREADY_EXISTS.getMessage());

        verify(eventPublisher, never()).publishEvent(any());
    }
}

