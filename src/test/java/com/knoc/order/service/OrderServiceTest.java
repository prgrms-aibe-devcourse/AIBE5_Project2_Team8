package com.knoc.order.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.global.lock.MysqlNamedLockRepository;
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

    @Mock
    private MysqlNamedLockRepository namedLockRepository;

    // 락 획득 성공 + 기존 주문 없음: 서비스가 생성 분기까지 들어가도록 공통 스텁
    private void stubLockSuccessAndNoExistingOrder(String idempotencyKey) {
        given(namedLockRepository.getLock(anyString(), anyInt())).willReturn(true);
        given(orderRepository.findByOrderNumber("ORD-" + idempotencyKey)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("결제 요청 성공: 정상적인 데이터가 입력되면 주문이 PENDING 상태로 생성된다.")
    void createOrderRequest_Success() {
        // given
        Long seniorId = 1L;
        Long juniorId = 2L;
        Long chatRoomId = 10L;
        OrderRequest request = new OrderRequest(chatRoomId, juniorId, 50000);
        String idempotencyKey = "test-idempotency-key";

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
        // willAnswer를 사용하여 저장 시도된 Order 객체를 그대로 반환 (DB 의존성 제거)
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        stubLockSuccessAndNoExistingOrder(idempotencyKey);

        // when
        OrderResponse response = orderService.createOrderRequest(request, seniorId, idempotencyKey);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualTo(50000);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderNumber()).startsWith("ORD-");

        // Verify: 주문이 실제로 한 번만 저장되었는지
        verify(orderRepository, times(1)).save(any(Order.class));
        // Verify: 결제 요청 시스템 메시지가 한 번만 저장되었고, 타입·문구가 기대와 맞는지
        verify(chatMessageRepository, times(1)).save(argThat(message ->
                message.getMessageType() == MessageType.PAYMENT_REQUESTED && // 타입 확인
                        message.getContent().contains("50,000") && // 금액 포함 확인
                        message.getContent().contains("결제를 완료하시면"))); // 문구 포함 확인

        // Verify(락): 서비스가 사용하는 lockKey(pay: + orderNumber)로 GET_LOCK이 호출됐는지 (timeout 초는 값만 검증)
        verify(namedLockRepository, times(1)).getLock(eq("pay:ORD-" + idempotencyKey), anyInt());
        // Verify(락): 예외 없이 끝나도 락 해제가 반드시 1회 시도되는지
        verify(namedLockRepository, times(1)).releaseLock(eq("pay:ORD-" + idempotencyKey));
    }

    @Test
    @DisplayName("멱등 요청: 동일 orderNumber가 이미 존재하면 새로 저장하지 않고 기존 주문을 반환한다.")
    void createOrderRequest_Idempotent_ReturnExistingOrder() {
        // given
        Long seniorId = 1L;
        Long chatRoomId = 10L;
        String idempotencyKey = "test-idempotency-key";
        OrderRequest request = new OrderRequest(chatRoomId, 2L, 50000);

        // orderRepository.findByOrderNumber() 단계에서 바로 반환되므로, 아래 엔티티 조회들은 호출되지 않아야 한다.
        Order existingOrder = mock(Order.class);
        ChatRoom existingChatRoom = mock(ChatRoom.class);

        given(existingOrder.getOrderNumber()).willReturn("ORD-" + idempotencyKey);
        given(existingOrder.getChatRoom()).willReturn(existingChatRoom);
        given(existingChatRoom.getId()).willReturn(chatRoomId);
        given(existingOrder.getAmount()).willReturn(50000);
        given(existingOrder.getStatus()).willReturn(OrderStatus.PENDING);

        given(namedLockRepository.getLock(anyString(), anyInt())).willReturn(true);
        given(orderRepository.findByOrderNumber("ORD-" + idempotencyKey)).willReturn(Optional.of(existingOrder));

        // when
        OrderResponse response = orderService.createOrderRequest(request, seniorId, idempotencyKey);

        // then
        assertThat(response.getOrderNumber()).isEqualTo("ORD-" + idempotencyKey);
        assertThat(response.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(response.getAmount()).isEqualTo(50000);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);

        // Verify: 멱등 경로에서는 주문/채팅 메시지 저장과 추가 조회가 일어나면 안 됨
        verify(orderRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
        verify(chatRoomRepository, never()).findById(anyLong());
        verify(memberRepository, never()).findById(anyLong());

        // Verify(락): 멱등 응답이어도 GET_LOCK 후 락 해제가 1회 시도되는지
        verify(namedLockRepository, times(1)).getLock(eq("pay:ORD-" + idempotencyKey), anyInt());
        verify(namedLockRepository, times(1)).releaseLock(eq("pay:ORD-" + idempotencyKey));
    }

    @Test
    @DisplayName("결제 요청 실패: 요청자가 해당 채팅방의 시니어가 아니면 예외가 발생한다.")
    void createOrderRequest_Fail_NotSenior() {
        // given
        Long actualSeniorId = 1L;
        Long hackerId = 99L; // 실제 방 주인(1L)과 다른 요청자 ID
        Long chatRoomId = 10L;
        String idempotencyKey = "test-idempotency-key";
        OrderRequest request = new OrderRequest(chatRoomId, 2L, 50000);

        ChatRoom chatRoom = mock(ChatRoom.class);
        Member actualSenior = mock(Member.class);

        // Stubbing: 채팅방의 실제 소유주 설정
        given(actualSenior.getId()).willReturn(actualSeniorId);
        given(chatRoom.getSenior()).willReturn(actualSenior);

        // Stubbing: Repository 조회 시나리오 설정
        given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(anyLong())).willReturn(Optional.of(mock(Member.class)));
        stubLockSuccessAndNoExistingOrder(idempotencyKey);

        // when & then
        assertThatThrownBy(() -> orderService.createOrderRequest(request, hackerId, idempotencyKey))
                .isInstanceOf(BusinessException.class) // BusinessException이 터져야 함
                .hasMessage(ErrorCode.NOT_SENIOR_IN_ROOM.getMessage()); // 메시지도 일치해야 함

        // Verify: 비즈니스 예외로 중단됐을 때도 주문/시스템 메시지는 저장되지 않아야 함
        verify(orderRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());

        // Verify(락): 예외가 나도 락 해제는 반드시 시도되어야 하므로 GET_LOCK / RELEASE_LOCK 둘 다 1회
        verify(namedLockRepository, times(1)).getLock(eq("pay:ORD-" + idempotencyKey), anyInt());
        verify(namedLockRepository, times(1)).releaseLock(eq("pay:ORD-" + idempotencyKey));
    }

    @Test
    @DisplayName("결제 요청 실패: 존재하지 않는 채팅방 ID로 요청하면 404 예외가 발생한다.")
    void createOrderRequest_Fail_NotFoundChatRoom() {
        // given
        String idempotencyKey = "test-idempotency-key";
        given(chatRoomRepository.findById(anyLong())).willReturn(Optional.empty());
        stubLockSuccessAndNoExistingOrder(idempotencyKey);
        OrderRequest request = new OrderRequest(1L, 2L, 10000);

        // when & then
        assertThatThrownBy(() -> orderService.createOrderRequest(request, 1L, idempotencyKey))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CHATROOM_NOT_FOUND.getMessage());

        // Verify: 채팅방 없음으로 중단될 때도 주문/시스템 메시지는 저장되지 않아야 함
        verify(orderRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());

        // Verify(락): 위와 동일 — 예외 발생 후에도 락 해제가 시도되는지 보장
        verify(namedLockRepository, times(1)).getLock(eq("pay:ORD-" + idempotencyKey), anyInt());
        verify(namedLockRepository, times(1)).releaseLock(eq("pay:ORD-" + idempotencyKey));
    }

    @Test
    @DisplayName("결제 성공: PENDING 주문이 PAID로 전이되고 결제완료 시스템 메시지가 1회 저장된다.")
    void payOrder_Success_PendingToPaid() {
        // given
        Long orderId = 100L;
        Long juniorId = 2L;
        String idempotencyKey = "idem-123";

        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(10L);

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("ORD-TEST")
                .chatRoom(chatRoom)
                .junior(junior)
                .senior(mock(Member.class))
                .amount(50000)
                .build(); // status = PENDING

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.payOrder(orderId, idempotencyKey, juniorId);

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);

        verify(orderRepository, times(1)).saveAndFlush(any(Order.class));
        verify(chatMessageRepository, times(1))
                .save(argThat(message -> message.getMessageType() == MessageType.PAYMENT_COMPLETED &&
                        message.getContent().contains("결제가 성공적으로 처리되었습니다.")));
    }

    @Test
    @DisplayName("결제 멱등: 이미 PAID면 저장/메시지 없이 그대로 응답한다.")
    void payOrder_Idempotent_WhenAlreadyPaid() {
        // given
        Long orderId = 101L;
        Long juniorId = 2L;
        String idempotencyKey = "idem-456";

        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(10L);

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(juniorId);

        Order order = Order.builder()
                .orderNumber("ORD-PAID")
                .chatRoom(chatRoom)
                .junior(junior)
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        OrderResponse response = orderService.payOrder(orderId, idempotencyKey, juniorId);

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);

        verify(orderRepository, never()).saveAndFlush(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 실패: 주문의 주니어가 아니면 403 예외가 발생하고 저장/메시지가 발생하지 않는다.")
    void payOrder_Fail_NotJunior() {
        // given
        Long orderId = 102L;
        Long actualJuniorId = 2L;
        Long attackerJuniorId = 999L;
        String idempotencyKey = "idem-789";

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(actualJuniorId);

        Order order = Order.builder()
                .orderNumber("ORD-NOT-JUNIOR")
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(mock(Member.class))
                .amount(50000)
                .build();

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.payOrder(orderId, idempotencyKey, attackerJuniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_JUNIOR_FOR_ORDER.getMessage());

        verify(orderRepository, never()).saveAndFlush(any());
        verify(chatMessageRepository, never()).save(any());
    }
}