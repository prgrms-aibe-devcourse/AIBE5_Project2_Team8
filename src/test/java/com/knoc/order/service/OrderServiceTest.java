package com.knoc.order.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito 가짜 객체 기능을 사용함
class OrderServiceTest {

    @InjectMocks // 아래 선언된 @Mock(가짜) 객체들을 OrderService의 생성자에 자동으로 주입해줌
    private OrderService orderService;

    // 가짜 객체 생성 (DB 연결 안 함)
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("결제 요청 성공: 정상적인 데이터가 입력되면 주문이 PENDING 상태로 생성된다.")
    void createOrderRequest_Success() {
        // given
        Long seniorId = 1L;
        Long juniorId = 2L;
        Long chatRoomId = 10L;
        OrderRequest request = new OrderRequest(chatRoomId, juniorId, 50000);

        // 테스트에 필요한 도메인 객체들도 Mock으로 생성
        Member senior = mock(Member.class);
        Member junior = mock(Member.class);
        ChatRoom chatRoom = mock(ChatRoom.class);

        // Stubbing: 도메인 객체 간의 관계 및 상태 정의
        given(senior.getId()).willReturn(seniorId);
        given(chatRoom.getSenior()).willReturn(senior);

        // Stubbing: Repository 조회 시나리오 설정 (DB 의존성 제거)
        given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(juniorId)).willReturn(Optional.of(junior));
        given(memberRepository.findById(seniorId)).willReturn(Optional.of(senior));

        // Stubbing: 데이터 저장 로직 모의 처리
        // willAnswer를 사용하여 저장 시도된 Order 객체를 ID값만 임의로 채워 반환 (referenceId 확인용)
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            // 실제 DB 저장이 아니므로 Reflection 등을 쓰지 않고 객체 그대로 반환
            // (Service에서 savedOrder.getId()를 쓰므로 이 테스트 환경에서는 null이 반환되어도 로직은 수행됨)
            return invocation.getArgument(0);
        });

        // when
        OrderResponse response = orderService.createOrderRequest(request, seniorId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualTo(50000);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderNumber()).startsWith("ORD-");

        // Verify: 실제로 DB 저장 메서드가 '한 번' 호출되었는지 확인
        verify(orderRepository, times(1)).save(any(Order.class));
        // Verify: 결제 요청 시스템 메시지 내용 검증
        verify(chatMessageRepository, times(1)).save(argThat(message ->
                message.getMessageType() ==  MessageType.PAYMENT_REQUESTED && // 타입 확인
                message.getContent().contains("50,000") && // 금액 포함 확인
                message.getContent().contains("결제를 완료하시면"))); // 문구 포함 확인
    }

    @Test
    @DisplayName("결제 요청 실패: 요청자가 해당 채팅방의 시니어가 아니면 예외가 발생한다.")
    void createOrderRequest_Fail_NotSenior() {
        // given
        Long actualSeniorId = 1L;
        Long hackerId = 99L; // 실제 방 주인(1L)과 다른 요청자 ID
        Long chatRoomId = 10L;
        OrderRequest request = new OrderRequest(chatRoomId, 2L, 50000);

        ChatRoom chatRoom = mock(ChatRoom.class);
        Member actualSenior = mock(Member.class);

        // Stubbing: 채팅방의 실제 소유주 설정
        given(actualSenior.getId()).willReturn(actualSeniorId);
        given(chatRoom.getSenior()).willReturn(actualSenior);

        // Stubbing: Repository 조회 시나리오 설정
        given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(anyLong())).willReturn(Optional.of(mock(Member.class)));

        // when & then
        assertThatThrownBy(() -> orderService.createOrderRequest(request, hackerId))
                .isInstanceOf(BusinessException.class) // BusinessException이 터져야 함
                .hasMessage(ErrorCode.NOT_SENIOR_IN_ROOM.getMessage()); // 메시지도 일치해야 함

        // Verify: 예외 발생 시 어떠한 데이터 저장도 일어나지 않아야 함
        verify(orderRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 요청 실패: 존재하지 않는 채팅방 ID로 요청하면 404 예외가 발생한다.")
    void createOrderRequest_Fail_NotFoundChatRoom() {
        // given
        given(chatRoomRepository.findById(anyLong())).willReturn(Optional.empty());
        OrderRequest request = new OrderRequest(1L, 2L, 10000);

        // when & then
        assertThatThrownBy(() -> orderService.createOrderRequest(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CHATROOM_NOT_FOUND.getMessage());

        // Verify: 예외 발생 시 어떠한 데이터 저장도 일어나지 않아야 함
        verify(orderRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
    }
}