package com.knoc.order.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
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
import com.knoc.senior.repository.SeniorProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDateTime;
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
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SeniorProfileRepository seniorProfileRepository;

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
        given(chatRoom.getId()).willReturn(chatRoomId);

        // Stubbing: Repository 조회 시나리오 설정 (DB 의존성 제거)
        given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(juniorId)).willReturn(Optional.of(junior));
        given(memberRepository.findById(seniorId)).willReturn(Optional.of(senior));

        // Stubbing: 데이터 저장 로직 모의 처리
        // willAnswer를 사용하여 저장 시도된 Order 객체를 ID값만 임의로 채워 반환 (referenceId 확인용)
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 1L);
            return invocation.getArgument(0);
        });

        // when
        OrderResponse response = orderService.createOrderRequest(request, seniorId, "idempotencyKey");

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualTo(50000);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderNumber()).startsWith("ORD-");

        // Verify: 실제로 DB 저장 메서드가 '한 번' 호출되었는지 확인
        verify(orderRepository, times(1)).saveAndFlush(any(Order.class));
        // Verify: 결제 요청 시스템 메시지 내용 검증
        // 직접 저장(Repository) 대신 이벤트 발행 여부 검증
        // ArgumentCaptor를 사용해 발행된 이벤트를 낚아챔.
        ArgumentCaptor<ChatSystemEvent> eventCaptor = ArgumentCaptor.forClass(ChatSystemEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        // 낚아챈 이벤트를 꺼내서 값들을 하나씩 검증
        ChatSystemEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.roomId()).isEqualTo(chatRoomId);
        assertThat(capturedEvent.type()).isEqualTo(MessageType.PAYMENT_REQUESTED);
        assertThat(capturedEvent.customContent()).contains("50,000");
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
        assertThatThrownBy(() -> orderService.createOrderRequest(request, hackerId, "idempotencyKey"))
                .isInstanceOf(BusinessException.class) // BusinessException이 터져야 함
                .hasMessage(ErrorCode.NOT_SENIOR_IN_ROOM.getMessage()); // 메시지도 일치해야 함

        // Verify: 예외 발생 시 어떠한 데이터 저장도 일어나지 않아야 함
        verify(orderRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any()); // 실패 시 이벤트 발행 안 됨
    }

    @Test
    @DisplayName("결제 요청 실패: 존재하지 않는 채팅방 ID로 요청하면 404 예외가 발생한다.")
    void createOrderRequest_Fail_NotFoundChatRoom() {
        // given
        given(chatRoomRepository.findById(anyLong())).willReturn(Optional.empty());
        OrderRequest request = new OrderRequest(1L, 2L, 10000);

        // when & then
        assertThatThrownBy(() -> orderService.createOrderRequest(request, 1L, "idempotencyKey"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CHATROOM_NOT_FOUND.getMessage());

        // Verify: 예외 발생 시 어떠한 데이터 저장도 일어나지 않아야 함
        verify(orderRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제창 요청 성공: PENDING 주문이면 상태 변경 없이 주문 정보를 반환한다.")
    void preparePayment_Success_ReturnsOrderWithoutStateChange() {
        // given
        Long orderId = 100L;
        Long juniorId = 2L;

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
        given(seniorProfileRepository.findByMemberId(any())).willReturn(Optional.empty());

        // when
        OrderResponse response = orderService.preparePayment(orderId, juniorId);

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getOrderNumber()).isEqualTo("ORD-TEST");
        assertThat(response.getAmount()).isEqualTo(50000);

        verify(orderRepository, never()).saveAndFlush(any(Order.class));

    }

    @Test
    @DisplayName("결제창 요청 멱등: 이미 PAID면 저장/메시지 없이 그대로 응답한다.")
    void preparePayment_Idempotent_WhenAlreadyPaid() {
        // given
        Long orderId = 101L;
        Long juniorId = 2L;

        ChatRoom chatRoom = mock(ChatRoom.class);
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
        OrderResponse response = orderService.preparePayment(orderId, juniorId);

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);

        verify(orderRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 승인 성공: 토스 confirm 이후 호출되면 PENDING 주문이 PAID로 전이되고 결제완료 시스템 메시지가 1회 저장된다.")
    void confirmPayment_Success_PendingToPaid() {
        // given
        String tossOrderId = "ORD-TEST";
        long confirmedAmount = 50000L;

        ChatRoom chatRoom = mock(ChatRoom.class);
        Member junior = mock(Member.class);

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(chatRoom)
                .junior(junior)
                .senior(mock(Member.class))
                .amount((int) confirmedAmount)
                .build(); // status = PENDING

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        Optional<OrderResponse> responseOpt = orderService.confirmPayment(tossOrderId, confirmedAmount);

        // then
        assertThat(responseOpt).isPresent();
        assertThat(responseOpt.get().getOrderStatus()).isEqualTo(OrderStatus.PAID);

        verify(orderRepository, times(1)).saveAndFlush(any(Order.class));

        // 결제 완료 이벤트 검증
        // - PAYMENT_COMPLETED: 시니어/주니어 공통
        // - REVIEW_REQUESTED: 주니어 전용(실시간 전송)
        ArgumentCaptor<ChatSystemEvent> eventCaptor = ArgumentCaptor.forClass(ChatSystemEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(ChatSystemEvent::type)
                .containsExactlyInAnyOrder(MessageType.PAYMENT_COMPLETED, MessageType.REVIEW_REQUESTED);

        ChatSystemEvent reviewRequested = eventCaptor.getAllValues().stream()
                .filter(e -> e.type() == MessageType.REVIEW_REQUESTED)
                .findFirst()
                .orElseThrow();
        assertThat(reviewRequested.sendToJunior()).isTrue();
        assertThat(reviewRequested.sendToSenior()).isFalse();
    }

    @Test
    @DisplayName("결제 실패: 주문의 주니어가 아니면 403 예외가 발생하고 저장/메시지가 발생하지 않는다.")
    void payOrder_Fail_NotJunior() {
        // given
        Long orderId = 102L;
        Long actualJuniorId = 2L;
        Long attackerJuniorId = 999L;

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
        assertThatThrownBy(() -> orderService.preparePayment(orderId, attackerJuniorId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_JUNIOR_FOR_ORDER.getMessage());

        verify(orderRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 요청 실패: 멱등키가 너무 짧으면 예외가 발생한다.")
    void createOrderRequest_Fail_InvalidIdempotencyKey() {
        // given
        String shortKey = "short"; // 10자 미만
        OrderRequest request = new OrderRequest(1L, 2L, 50000);

        // when & then
        assertThatThrownBy(() -> orderService.createOrderRequest(request, 1L, shortKey))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_IDEMPOTENCY_KEY.getMessage());

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("결제 요청 멱등: 동시에 두 요청이 들어와 DB 충돌이 발생해도 기존 주문을 반환한다.")
    void createOrderRequest_Success_WhenConcurrencyConflict() {
        // given
        Long seniorId = 1L;
        Long juniorId = 2L;
        OrderRequest request = new OrderRequest(10L, juniorId, 50000);
        String idempotencyKey = "idempotency-789";
        String orderNumber = "ORD-" + idempotencyKey;

        // 1. 필요한 연관 엔티티들을 Mock으로 준비 (OrderResponse.from에서 필드 추출 시 필요)
        ChatRoom mockChatRoom = mock(ChatRoom.class);
        given(mockChatRoom.getId()).willReturn(10L);

        Member mockJunior = mock(Member.class);

        Member mockSenior = mock(Member.class);
        given(mockSenior.getId()).willReturn(1L);

        // 2. 실제 Order 객체 생성 (Mock 대신 Builder 사용)
        Order existingOrder = Order.builder()
                .orderNumber(orderNumber)
                .amount(50000)
                .chatRoom(mockChatRoom)
                .junior(mockJunior)
                .senior(mockSenior)
                .build();

        // 만약 필드에 직접 접근하거나 getId() 등이 필요하면 ReflectionTestUtils 사용
        org.springframework.test.util.ReflectionTestUtils.setField(existingOrder, "id", 100L);

        // 3. Stubbing 설정
        given(orderRepository.findByOrderNumber(orderNumber))
                .willReturn(Optional.empty()) // 첫번째 조회: Optional.empty 반환
                .willReturn(Optional.of(existingOrder)); // 두번째 조회: existingOrder 반환

        // 저장 시도 시 누군가 먼저 저장해서 충돌 발생
        given(orderRepository.saveAndFlush(any(Order.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate Entry"));

        // 나머지 엔티티 조회 Stubbing (orElseGet 내부 진입 시 필요)
        given(chatRoomRepository.findById(anyLong())).willReturn(Optional.of(mockChatRoom));
        given(memberRepository.findById(juniorId)).willReturn(Optional.of(mockJunior));
        given(memberRepository.findById(seniorId)).willReturn(Optional.of(mockSenior));

        given(mockChatRoom.getSenior()).willReturn(mockSenior);

        // when
        OrderResponse response = orderService.createOrderRequest(request, seniorId, idempotencyKey);

        // then
        assertThat(response.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(response.getAmount()).isEqualTo(50000);
        verify(orderRepository, times(2)).findByOrderNumber(orderNumber); // 처음 + catch문 재조회
    }

    // ============================================================
    // A. confirmPayment 승인 멱등성
    // ============================================================

    @Test
    @DisplayName("결제 승인 멱등: 이미 PAID인 주문에 confirm 재호출되면 저장/이벤트 없이 기존 응답 반환")
    void confirmPayment_Idempotent_WhenAlreadyPaid() {
        // given
        String tossOrderId = "ORD-PAID";
        long confirmedAmount = 50000L;

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount((int) confirmedAmount)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));

        // when
        Optional<OrderResponse> responseOpt = orderService.confirmPayment(tossOrderId, confirmedAmount);

        // then
        assertThat(responseOpt).isPresent();
        assertThat(responseOpt.get().getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 승인: 로컬에 없는 주문(예: TEST-...)은 Optional.empty() 반환")
    void confirmPayment_Empty_WhenOrderNotFound() {
        // given
        String tossOrderId = "TEST-UNKNOWN";
        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.empty());

        // when
        Optional<OrderResponse> responseOpt = orderService.confirmPayment(tossOrderId, 10000L);

        // then
        assertThat(responseOpt).isEmpty();
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 승인 멱등: 낙관적 락 충돌 후 재조회 시 PAID면 기존 응답 반환(이벤트 미발행)")
    void confirmPayment_Recovers_WhenOptimisticLockAndAlreadyPaidByOtherTx() {
        // given
        String tossOrderId = "ORD-LOCK";
        long confirmedAmount = 50000L;
        Long orderId = 200L;

        Order current = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount((int) confirmedAmount)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(current, "id", orderId);

        Order latestPaid = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount((int) confirmedAmount)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(latestPaid, "id", orderId);
        latestPaid.updateStatus(OrderStatus.PAID);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(current));
        given(orderRepository.saveAndFlush(any(Order.class)))
                .willThrow(new OptimisticLockingFailureException("conflict"));
        given(orderRepository.findById(orderId)).willReturn(Optional.of(latestPaid));

        // when
        Optional<OrderResponse> responseOpt = orderService.confirmPayment(tossOrderId, confirmedAmount);

        // then
        assertThat(responseOpt).isPresent();
        assertThat(responseOpt.get().getOrderStatus()).isEqualTo(OrderStatus.PAID);
        // 충돌 후 재조회 경로에선 결제완료 이벤트가 중복 발행되지 않아야 함
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 승인 충돌: 낙관적 락 충돌 후 재조회 시 여전히 PAID가 아니면 ORDER_PAYMENT_CONFLICT(409) 예외")
    void confirmPayment_Throws_WhenOptimisticLockAndStillNotPaid() {
        // given
        String tossOrderId = "ORD-LOCK2";
        long confirmedAmount = 50000L;
        Long orderId = 201L;

        Order current = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount((int) confirmedAmount)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(current, "id", orderId);

        Order latestStillPending = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount((int) confirmedAmount)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(latestStillPending, "id", orderId);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(current));
        given(orderRepository.saveAndFlush(any(Order.class)))
                .willThrow(new OptimisticLockingFailureException("conflict"));
        given(orderRepository.findById(orderId)).willReturn(Optional.of(latestStillPending));

        // when & then
        assertThatThrownBy(() -> orderService.confirmPayment(tossOrderId, confirmedAmount))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_PAYMENT_CONFLICT.getMessage());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 승인 사후 금액 불일치: ORDER_PAYMENT_AMOUNT_MISMATCH 예외 + 저장/이벤트 없음")
    void confirmPayment_Throws_OnAmountMismatch() {
        // given
        String tossOrderId = "ORD-AMT";
        int orderAmount = 50000;
        long confirmedAmount = 10000L; // 불일치

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(orderAmount)
                .build();

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.confirmPayment(tossOrderId, confirmedAmount))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH.getMessage());

        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ============================================================
    // B. verifyPaymentAmount 사전 금액 검증
    // ============================================================

    @Test
    @DisplayName("사전 금액 검증: 음수 amount면 ORDER_INVALID_AMOUNT 예외(주문 조회 이전에 차단)")
    void verifyPaymentAmount_Throws_OnNegativeAmount() {
        // when & then
        assertThatThrownBy(() -> orderService.verifyPaymentAmount("ORD-NEG", -1))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_INVALID_AMOUNT.getMessage());

        // 음수면 조회까지 가지 않아야 함
        verify(orderRepository, never()).findByOrderNumber(any());
    }

    @Test
    @DisplayName("사전 금액 검증 멱등: 이미 PAID면 예외 없이 조용히 리턴(금액 달라도 무시)")
    void verifyPaymentAmount_NoOp_WhenAlreadyPaid() {
        // given
        String tossOrderId = "ORD-PAID-VERIFY";
        int amount = 50000;

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(amount)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));

        // when & then (예외가 나지 않아야 함: PAID면 금액 비교 전에 리턴)
        orderService.verifyPaymentAmount(tossOrderId, amount + 999);
    }

    @Test
    @DisplayName("사전 금액 검증: 로컬에 없는 주문이면 조용히 리턴(confirmPayment와 동일 정책)")
    void verifyPaymentAmount_NoOp_WhenOrderNotFound() {
        // given
        given(orderRepository.findByOrderNumber("TEST-UNKNOWN")).willReturn(Optional.empty());

        // when & then (예외 없음)
        orderService.verifyPaymentAmount("TEST-UNKNOWN", 50000);
    }

    @Test
    @DisplayName("사전 금액 검증: 주문 금액과 불일치 시 ORDER_PAYMENT_AMOUNT_MISMATCH 예외")
    void verifyPaymentAmount_Throws_OnAmountMismatch() {
        // given
        String tossOrderId = "ORD-AMT-PRE";

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(50000)
                .build();

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.verifyPaymentAmount(tossOrderId, 10000))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH.getMessage());
    }

    // ============================================================
    // C. recordPaymentFailure 실패 멱등성
    // ============================================================

    @Test
    @DisplayName("결제 실패 멱등: 이미 PAID 주문이면 실패 메시지 발행 안 함(뒤늦은 fail 콜백 방어)")
    void recordPaymentFailure_Skips_WhenAlreadyPaid() {
        // given
        String tossOrderId = "ORD-FAIL-PAID";

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        order.updateStatus(OrderStatus.PAID);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));

        // when
        orderService.recordPaymentFailure(tossOrderId, "뒤늦게 도달한 실패 콜백");

        // then (방어선1: 상태 체크에서 먼저 걸러져 쿨다운 조회까지 가지 않음)
        verify(eventPublisher, never()).publishEvent(any());
        verify(chatMessageRepository, never())
                .existsByReferenceIdAndMessageTypeAndCreatedAtAfter(any(), any(), any());
    }

    @Test
    @DisplayName("결제 실패 멱등: 이미 SETTLED 주문이면 실패 메시지 발행 안 함")
    void recordPaymentFailure_Skips_WhenAlreadySettled() {
        // given
        String tossOrderId = "ORD-FAIL-SETTLED";

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        // PENDING -> PAID -> SETTLED (Order.validateTransition 순서에 맞춰 두 단계로 전이)
        order.updateStatus(OrderStatus.PAID);
        order.updateStatus(OrderStatus.SETTLED);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));

        // when
        orderService.recordPaymentFailure(tossOrderId, "이미 정산됨");

        // then
        verify(eventPublisher, never()).publishEvent(any());
        verify(chatMessageRepository, never())
                .existsByReferenceIdAndMessageTypeAndCreatedAtAfter(any(), any(), any());
    }

    @Test
    @DisplayName("결제 실패 멱등: 최근 30초 내 PAYMENT_FAILED 이력이 있으면 중복 발행 스킵(쿨다운)")
    void recordPaymentFailure_Skips_WhenRecentFailureExists() {
        // given
        String tossOrderId = "ORD-FAIL-COOLDOWN";
        Long orderId = 300L;

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(mock(ChatRoom.class))
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", orderId);
        // status = PENDING (기본값)

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));
        given(chatMessageRepository.existsByReferenceIdAndMessageTypeAndCreatedAtAfter(
                eq(orderId), eq(MessageType.PAYMENT_FAILED), any(LocalDateTime.class)))
                .willReturn(true);

        // when
        orderService.recordPaymentFailure(tossOrderId, "재시도 콜백");

        // then
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("결제 실패 정상 경로: PENDING + 쿨다운 없음 → PAYMENT_FAILED 이벤트 1회(템플릿) 발행")
    void recordPaymentFailure_Publishes_WhenEligible() {
        // given
        String tossOrderId = "ORD-FAIL-OK";
        Long orderId = 301L;
        Long chatRoomId = 10L;

        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(chatRoomId);

        Order order = Order.builder()
                .orderNumber(tossOrderId)
                .chatRoom(chatRoom)
                .junior(mock(Member.class))
                .senior(mock(Member.class))
                .amount(50000)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", orderId);

        given(orderRepository.findByOrderNumber(tossOrderId)).willReturn(Optional.of(order));
        given(chatMessageRepository.existsByReferenceIdAndMessageTypeAndCreatedAtAfter(
                eq(orderId), eq(MessageType.PAYMENT_FAILED), any(LocalDateTime.class)))
                .willReturn(false);

        // when
        orderService.recordPaymentFailure(tossOrderId, "카드 한도 초과");

        // then: 이벤트 1회, 템플릿 사용(customContent == null), referenceId 일치
        ArgumentCaptor<ChatSystemEvent> captor = ArgumentCaptor.forClass(ChatSystemEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        ChatSystemEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(MessageType.PAYMENT_FAILED);
        assertThat(event.customContent()).isNull();
        assertThat(event.referenceId()).isEqualTo(orderId);
        assertThat(event.roomId()).isEqualTo(chatRoomId);
    }

    @Test
    @DisplayName("결제 실패: 로컬에 없는 주문이면 아무 것도 하지 않음(상태/메시지/로그 영향 없음)")
    void recordPaymentFailure_NoOp_WhenOrderNotFound() {
        // given
        given(orderRepository.findByOrderNumber("TEST-UNKNOWN")).willReturn(Optional.empty());

        // when
        orderService.recordPaymentFailure("TEST-UNKNOWN", "사유");

        // then
        verify(eventPublisher, never()).publishEvent(any());
        verify(chatMessageRepository, never())
                .existsByReferenceIdAndMessageTypeAndCreatedAtAfter(any(), any(), any());
    }
}