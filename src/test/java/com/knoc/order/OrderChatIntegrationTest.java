package com.knoc.order;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
//import com.knoc.chat.entity.ChatRoomStatus;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderChatIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("결제 요청 시 주문이 생성되고, 웹소켓 시스템 메시지가 발송된다")
    void createOrder_PublishesChatEvent() {
        // given
        Member junior = memberRepository.save(Member.builder()
                .nickname("주니어")
                .email("junior@test.com")
                .password("password!")
                .build());

        Member senior = memberRepository.save(Member.builder()
                .nickname("시니어")
                .email("senior@test.com")
                .password("password!")
                .build());

        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .junior(junior)
                .senior(senior)
                //.status(ChatRoomStatus.ACTIVE)
                .build());

        // 세팅된 실제 ID값을 사용하여 Request DTO 생성
        OrderRequest request = new OrderRequest(chatRoom.getId(), junior.getId(), 55000);

        // when: 시니어가 결제 요청을 실행
        OrderResponse response = orderService.createOrderRequest(request, senior.getId());

        // then 1: OrderResponse 검증
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualTo(55000);
        assertThat(response.getChatRoomId()).isEqualTo(chatRoom.getId());

        // then 2: DB에 채팅 메시지 검증
        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getContent()).contains("55,000원 결제를 요청했습니다");

        // then 3: 웹소켓 발송 검증
        String expectedDestination = "/topic/chat/" + chatRoom.getId();
        verify(messagingTemplate).convertAndSend(eq(expectedDestination), any(Object.class));
    }
}