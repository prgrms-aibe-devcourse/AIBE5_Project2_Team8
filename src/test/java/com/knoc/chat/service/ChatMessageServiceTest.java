package com.knoc.chat.service;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatMessageServiceTest {

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("메시지를 전송하면 DB에 저장되고 1:1 큐 방식으로 양쪽 유저에게 발송된다.")
    void sendMessage_Success() {
        // given
        Long roomId = 1L;
        Long senderId = 10L;
        String content = "안녕하세요";

        // 1. 참여자(Junior, Senior) Mock 생성
        Member junior = mock(Member.class);
        Member senior = mock(Member.class);

        // 💡 실제 로직에서 sender.getNickname()을 사용하므로 닉네임 스터빙 추가
        given(junior.getId()).willReturn(senderId);
        given(junior.getEmail()).willReturn("junior@test.com");
        given(junior.getNickname()).willReturn("주니어닉네임"); // 추가됨

        given(senior.getId()).willReturn(20L);
        given(senior.getEmail()).willReturn("senior@test.com");

        // 2. ChatRoom 생성 (ID 주입)
        ChatRoom chatRoom = ChatRoom.builder()
                .junior(junior)
                .senior(senior)
                .build();
        ReflectionTestUtils.setField(chatRoom, "id", roomId);

        // 3. Mock Repository 설정
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        given(memberRepository.findById(senderId)).willReturn(Optional.of(junior));

        // 4. 저장된 메시지 Mock (로직에서 사용하는 필드만 스터빙)
        ChatMessage mockSavedMessage = mock(ChatMessage.class);
        given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(mockSavedMessage);
        given(mockSavedMessage.getContent()).willReturn(content);

        // when
        chatMessageService.sendMessage(roomId, senderId, content);

        // then
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSendToUser(eq("junior@test.com"), eq("/queue/chat"), any(ChatMessageResponse.class));
        verify(messagingTemplate).convertAndSendToUser(eq("senior@test.com"), eq("/queue/chat"), any(ChatMessageResponse.class));
    }
}