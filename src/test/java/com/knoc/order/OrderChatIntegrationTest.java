package com.knoc.order;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
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
        junior = memberRepository.save(Member.builder()
                .nickname("주니어").email("junior@test.com").password("pass!").role(MemberRole.USER).build());
        senior = memberRepository.save(Member.builder()
                .nickname("시니어").email("senior@test.com").password("pass!").role(MemberRole.SENIOR).build());
        chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .junior(junior).senior(senior).build());
    }

    // 수동 커밋으로 인해 DB에 남은 데이터를 다음 테스트에 영향 가지 않게 싹 비워줌
    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        orderRepository.deleteAll();
        chatRoomRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("시니어가 결제 요청 시, 주문이 생성되고 시스템 메시지가 웹소켓으로 발송된다")
    void createOrder_IntegrationTest() {
        // given
        OrderRequest request = new OrderRequest(chatRoom.getId(), junior.getId(), 55000);

        // when
        OrderResponse response = orderService.createOrderRequest(request, senior.getId(), "idempotencyKey");

        // AFTER_COMMIT 리스너를 동작시키기 위해 트랜잭션을 여기서 수동으로 커밋
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // then
        assertThat(response.getAmount()).isEqualTo(55000);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);

        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).anyMatch(m -> m.getContent().contains("55,000원 결제를 요청"));

        // 1:1 Queue 전송 검증
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(eq(junior.getEmail()), eq("/queue/chat"), any(Object.class));
    }

    @Test
    @DisplayName("주니어가 결제 완료 시, 주문 상태가 PAID로 변경되고 완료 메시지가 발송된다")
    void payOrder_IntegrationTest() {
        // given
        Order order = orderRepository.save(Order.builder()
                .orderNumber("TEST-ORD").chatRoom(chatRoom).junior(junior).senior(senior).amount(55000).build());

        // when
        OrderResponse response = orderService.confirmPayment(order.getOrderNumber(), order.getAmount())
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // 핵심: AFTER_COMMIT 리스너를 동작시키기 위해 트랜잭션을 여기서 수동으로 커밋
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // then
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);

        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).anyMatch(m -> m.getContent().contains("결제가 성공적으로 처리"));

        // 1:1 Queue 전송 검증
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(eq(senior.getEmail()), eq("/queue/chat"), any(Object.class));
    }
}