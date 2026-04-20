package com.knoc.order;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole; // 프로젝트의 Role 위치에 맞게 수정
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.entity.Order; // 추가
import com.knoc.order.entity.OrderStatus; // 추가
import com.knoc.order.repository.OrderRepository; // 추가
import com.knoc.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderChatIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private OrderRepository orderRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Member junior;
    private Member senior;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        // 공통 데이터 세팅
        junior = memberRepository.save(Member.builder()
                .nickname("주니어").email("junior@test.com").password("pass!").role(MemberRole.USER).build());
        senior = memberRepository.save(Member.builder()
                .nickname("시니어").email("senior@test.com").password("pass!").role(MemberRole.SENIOR).build());
        chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .junior(junior).senior(senior).build());
    }

    @Test
    @DisplayName("시니어가 결제 요청 시, 주문이 생성되고 시스템 메시지가 웹소켓으로 발송된다")
    void createOrder_IntegrationTest() {
        // given
        OrderRequest request = new OrderRequest(chatRoom.getId(), junior.getId(), 55000);

        // when
        OrderResponse response = orderService.createOrderRequest(request, senior.getId());

        // then: DTO 확인
        assertThat(response.getAmount()).isEqualTo(55000);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);

        // then: DB 메시지 확인
        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).anyMatch(m -> m.getContent().contains("55,000원 결제를 요청"));

        // then: 웹소켓 발송 확인
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/chat/" + chatRoom.getId()), any(Object.class));
    }

    @Test
    @DisplayName("주니어가 결제 완료 시, 주문 상태가 PAID로 변경되고 완료 메시지가 발송된다")
    void payOrder_IntegrationTest() {
        // given: PENDING 상태의 주문이 먼저 있어야 함
        Order order = orderRepository.save(Order.builder()
                .orderNumber("TEST-ORD").chatRoom(chatRoom).junior(junior).senior(senior).amount(55000).build());

        // when
        OrderResponse response = orderService.confirmPayment(order.getOrderNumber(), order.getAmount())
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // then: 상태 변경 확인
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);

        // then: DB에 결제 완료 메시지 저장 확인
        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).anyMatch(m -> m.getContent().contains("결제가 성공적으로 처리"));

        // then: 웹소켓 발송 확인
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/chat/" + chatRoom.getId()), any(Object.class));
    }
}