package com.knoc.settlement.service;

import com.knoc.chat.entity.ChatRoom;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewFeedbackServiceTest {

    @InjectMocks
    private ReviewFeedbackService reviewFeedbackService;

    @Mock
    private ReviewFeedbackRepository reviewFeedbackRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private SeniorProfileRepository seniorProfileRepository;
    @Mock
    private MemberRepository memberRepository; // ReviewFeedbackService 생성자 주입 대상

    @Test
    @DisplayName("후기 작성 성공:PAID상태 주문 후기 작성, 시니어 프로필 avgRating 갱신")
    void createReview_Success() {
        // given
        Long juniorId = 1L;
        Long seniorMemberId = 2L;
        Long orderId = 10L;
        //Mock dto생성
        ReviewFeedbackRequestDto dto = mock(ReviewFeedbackRequestDto.class);
        given(dto.getOrderId()).willReturn(orderId);
        given(dto.getRating()).willReturn((byte) 4);
        given(dto.getComment()).willReturn("도움이 많이 됐어요!");
        //Mock junior회원 생성
        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);
        //Mock senior회원 생성
        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(seniorMemberId);

        //Order생성,Order 상태 PAID 전환
        Order order = Order.builder()
                .orderNumber("OrderTest1")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(seniorMember)
                .amount(50000)
                .build();
        order.updateStatus(OrderStatus.PAID);
        //Mock senior프로필 생성
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewFeedbackRepository.existsByOrderId(order.getId())).willReturn(false);
        given(seniorProfileRepository.findByMemberId(seniorMemberId)).willReturn(Optional.of(seniorProfile));
        given(reviewFeedbackRepository.save(any(ReviewFeedback.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        reviewFeedbackService.createReview(dto, juniorId);

        // then
        verify(reviewFeedbackRepository, times(1)).save(any(ReviewFeedback.class));
        verify(seniorProfile, times(1)).updateRating(4.0);
    }

    @Test
    @DisplayName("후기 작성 실패: 존재하지 않는 주문 ID로 요청시 ORDER_NOT_FOUND 예외 발생")
    void createReview_Fail_OrderNotFound() {
        // given
        ReviewFeedbackRequestDto dto = mock(ReviewFeedbackRequestDto.class);
        given(dto.getOrderId()).willReturn(999L);
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewFeedbackService.createReview(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());

        verify(reviewFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("후기 작성 실패: 요청자가 해당 주문의 주니어가 아니면 NOT_JUNIOR_FOR_ORDER 예외가 발생")
    void createReview_Fail_NotJuniorForOrder() {
        // given
        Long juniorId1 = 1L;
        Long juniorId2 = 99L;
        Long orderId = 10L;

        ReviewFeedbackRequestDto dto = mock(ReviewFeedbackRequestDto.class);
        given(dto.getOrderId()).willReturn(orderId);

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId1);

        Order order = Order.builder()
                .orderNumber("OrderTest2")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> reviewFeedbackService.createReview(dto, juniorId2))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_JUNIOR_FOR_ORDER.getMessage());

        verify(reviewFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("후기 작성 실패: 주문 상태가 PAID가 아니면 REVIEW_NOT_ALLOWED 예외가 발생")
    void createReview_Fail_OrderNotPaid() {
        // given
        Long juniorId = 1L;
        Long orderId = 10L;

        ReviewFeedbackRequestDto dto = mock(ReviewFeedbackRequestDto.class);
        given(dto.getOrderId()).willReturn(orderId);

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("OrderTest3")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(50000)
                .build(); // 기본 상태는 PENDING

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> reviewFeedbackService.createReview(dto, juniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_NOT_ALLOWED.getMessage());

        verify(reviewFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("후기 작성 실패: 해당 주문에 후기가 이미 존재하면 REVIEW_ALREADY_EXISTS 예외 발생.")
    void createReview_Fail_ReviewAlreadyExists() {
        // given
        Long juniorId = 1L;
        Long orderId = 10L;

        ReviewFeedbackRequestDto dto = mock(ReviewFeedbackRequestDto.class);
        given(dto.getOrderId()).willReturn(orderId);

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("OrderTest4")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewFeedbackRepository.existsByOrderId(order.getId())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> reviewFeedbackService.createReview(dto, juniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.REVIEW_ALREADY_EXISTS.getMessage());

        verify(reviewFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("후기 작성 실패: 시니어 프로필이 존재하지 않으면 SENIOR_PROFILE_NOT_FOUND 예외가 발생")
    void createReview_Fail_SeniorProfileNotFound() {
        // given
        Long juniorId = 1L;
        Long seniorMemberId = 2L;
        Long orderId = 10L;

        ReviewFeedbackRequestDto dto = mock(ReviewFeedbackRequestDto.class);
        given(dto.getOrderId()).willReturn(orderId);

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(seniorMemberId);

        Order order = Order.builder()
                .orderNumber("OrderTest5")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(seniorMember)
                .amount(50000)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(reviewFeedbackRepository.existsByOrderId(order.getId())).willReturn(false);
        given(seniorProfileRepository.findByMemberId(seniorMemberId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewFeedbackService.createReview(dto, juniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SENIOR_PROFILE_NOT_FOUND.getMessage());

        verify(reviewFeedbackRepository, never()).save(any());
    }
}