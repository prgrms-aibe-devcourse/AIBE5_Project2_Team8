package com.knoc.review.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.review.dto.ReviewRequestCreateRequest;
import com.knoc.review.dto.ReviewRequestCreateResponse;
import com.knoc.review.dto.ReviewRequestUpdateRequest;
import com.knoc.review.entity.ReviewRequest;
import com.knoc.review.repository.ReviewReportRepository;
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

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ReviewReportRepository reviewReportRepository;

    @Test
    @DisplayName("리뷰 요청서 작성 성공: PAID 주문에 대해 리뷰 요청서를 저장하고 REVIEW_SUBMITTED 이벤트를 발행한다.")
    void createReviewRequest_Success() {
        // given
        String email = "junior@test.com";
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
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

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
        ReviewRequestCreateResponse response = reviewRequestService.createReviewRequest(dto, email);

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
        String email = "junior@test.com";
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(mock(Member.class)));

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                999L,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 요청자가 해당 주문의 주니어가 아니면 NOT_JUNIOR_FOR_ORDER 예외가 발생한다.")
    void createReviewRequest_Fail_NotJuniorForOrder() {
        // given
        String email = "junior@test.com";
        Long orderId = 10L;

        ReviewRequestCreateRequest dto = new ReviewRequestCreateRequest(
                orderId,
                "https://github.com/user/repo/pull/1",
                "context",
                "concern"
        );

        Member orderJunior = mock(Member.class);
        given(orderJunior.getId()).willReturn(1L);

        Member requester = mock(Member.class);
        given(requester.getId()).willReturn(999L);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(requester));

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
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_JUNIOR_FOR_ORDER.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 주문 상태가 PAID가 아니면 REVIEW_REQUEST_NOT_ALLOWED 예외가 발생한다.")
    void createReviewRequest_Fail_OrderNotPaid() {
        // given
        String email = "junior@test.com";
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
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

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
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_REQUEST_NOT_ALLOWED.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 이미 리뷰 요청서가 존재하면 REVIEW_REQUEST_ALREADY_EXISTS 예외가 발생한다.")
    void createReviewRequest_Fail_AlreadyExists() {
        // given
        String email = "junior@test.com";
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
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

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
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_REQUEST_ALREADY_EXISTS.getMessage());

        verify(reviewRequestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 작성 실패: 저장 중 UNIQUE 제약 위반이면 REVIEW_REQUEST_ALREADY_EXISTS 예외로 변환한다.")
    void createReviewRequest_Fail_DataIntegrityViolation() {
        // given
        String email = "junior@test.com";
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
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

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
        assertThatThrownBy(() -> reviewRequestService.createReviewRequest(dto, email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_REQUEST_ALREADY_EXISTS.getMessage());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("리뷰 요청서 수정 성공: 리포트가 없고 요청서가 존재하면 요청서를 업데이트한다.")
    void updateReviewRequest_Success() {
        // given
        String email = "junior@test.com";
        Long juniorId = 1L;
        Long orderId = 10L;

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

        Order order = Order.builder()
            .orderNumber("ORD-TEST-KEY")
            .chatRoom(mock(ChatRoom.class))
            .junior(junior)
            .senior(mock(Member.class))
            .amount(15000)
            .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.updateStatus(OrderStatus.PAID);

        ReviewRequestUpdateRequest req = new ReviewRequestUpdateRequest(
            orderId,
            "https://github.com/user/repo/pull/2",
            "수정된 프로젝트 배경",
            "수정된 고민 포인트"
        );

        ReviewRequest rr = mock(ReviewRequest.class);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewReportRepository.existsByReviewRequest_Order_Id(orderId)).willReturn(false);
        given(reviewRequestRepository.findByOrder(order)).willReturn(Optional.of(rr));

        // when
        Long result = reviewRequestService.updateReviewRequest(email, req);

        // then
        assertThat(result).isEqualTo(orderId);
        verify(rr, times(1)).update(req.githubPrUrl(), req.projectContext(), req.concernPoint());
    }

    @Test
    @DisplayName("리뷰 요청서 수정 실패: 요청서가 없으면 REVIEW_REQUEST_NOT_FOUND 예외가 발생한다.")
    void updateReviewRequest_Fail_RequestNotFound() {
        // given
        String email = "junior@test.com";
        Long juniorId = 1L;
        Long orderId = 10L;

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

        Order order = Order.builder()
            .orderNumber("ORD-TEST-KEY")
            .chatRoom(mock(ChatRoom.class))
            .junior(junior)
            .senior(mock(Member.class))
            .amount(15000)
            .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.updateStatus(OrderStatus.PAID);

        ReviewRequestUpdateRequest req = new ReviewRequestUpdateRequest(
            orderId,
            "https://github.com/user/repo/pull/2",
            "수정된 프로젝트 배경",
            "수정된 고민 포인트"
        );

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewReportRepository.existsByReviewRequest_Order_Id(orderId)).willReturn(false);
        given(reviewRequestRepository.findByOrder(order)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewRequestService.updateReviewRequest(email, req))
            .isInstanceOf(BusinessException.class)
            .hasMessage(ErrorCode.REVIEW_REQUEST_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("리뷰 요청서 수정 실패: 리포트가 이미 존재하면 REVIEW_REPORT_ALREADY_EXISTS 예외가 발생한다.")
    void updateReviewRequest_Fail_ReportAlreadyExists() {
        // given
        String email = "junior@test.com";
        Long juniorId = 1L;
        Long orderId = 10L;

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

        Order order = Order.builder()
            .orderNumber("ORD-TEST-KEY")
            .chatRoom(mock(ChatRoom.class))
            .junior(junior)
            .senior(mock(Member.class))
            .amount(15000)
            .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.updateStatus(OrderStatus.PAID);

        ReviewRequestUpdateRequest req = new ReviewRequestUpdateRequest(
            orderId,
            "https://github.com/user/repo/pull/2",
            "수정된 프로젝트 배경",
            "수정된 고민 포인트"
        );

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewReportRepository.existsByReviewRequest_Order_Id(orderId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> reviewRequestService.updateReviewRequest(email, req))
            .isInstanceOf(BusinessException.class)
            .hasMessage(ErrorCode.REVIEW_REPORT_ALREADY_EXISTS.getMessage());
    }
}

