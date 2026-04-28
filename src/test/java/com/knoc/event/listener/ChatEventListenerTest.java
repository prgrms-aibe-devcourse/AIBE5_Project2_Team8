package com.knoc.event.listener;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.member.Member;
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatEventListenerTest {

    @InjectMocks
    private ChatEventListener chatEventListener;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private OrderRepository orderRepository;

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {
            "ROOM_CLOSE",
            "PAYMENT_REQUESTED",
            "PAYMENT_COMPLETED",
            "REPORT_COMPLETED",
            "WORKSPACE_READY"
    })
    @DisplayName("다양한 시스템 이벤트가 발생해도 모두 DB에 저장하고 유저들에게 1:1 큐로 발송한다.")
    void handleChatSystemEvent_Success_AllTypes(MessageType systemMessageType) {
        // given
        // 파라미터로 넘어온 systemMessageType을 사용합니다.
        ChatSystemEvent event = new ChatSystemEvent(1L, systemMessageType, "시스템 알림 테스트", 12345L);

        ChatRoom room = mock(ChatRoom.class);
        Member junior = mock(Member.class);
        Member senior = mock(Member.class);

        given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(room.getJunior()).willReturn(junior);
        given(room.getSenior()).willReturn(senior);
        given(junior.getEmail()).willReturn("junior@test.com");
        given(senior.getEmail()).willReturn("senior@test.com");

        if (systemMessageType == MessageType.PAYMENT_REQUESTED) {
            Order order = mock(Order.class);
            given(orderRepository.findById(12345L)).willReturn(Optional.of(order));
            given(order.getAmount()).willReturn(30000);
        }

        // when
        chatEventListener.handleChatEventSystem(event);

        // then
        // 1. DB에 해당 시스템 메시지가 정상적으로 저장되는지 확인
        verify(chatMessageRepository).save(argThat(message ->
                message.getMessageType() == systemMessageType
        ));

        // 2. 주니어와 시니어 양쪽 큐에 메시지가 발송되는지 확인 (총 2회)
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), eq("/queue/chat"), any(ChatMessageResponse.class));
    }
}