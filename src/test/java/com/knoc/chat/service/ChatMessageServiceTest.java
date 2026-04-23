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

    // ✅ 추가됨: ChatMessageService가 이제 ChatRoomService를 의존하므로 Mock 등록 필수!
    @Mock
    private ChatRoomService chatRoomService;

    @Test
    @DisplayName("메시지를 전송하면 DB에 저장되고 1:1 큐 방식으로 양쪽 유저에게 발송된다.")
    void sendMessage_Success() {
        // given
        Long roomId = 1L;
        String senderEmail = "junior@test.com"; // ✅ 변경됨: senderId 대신 email 사용
        String content = "안녕하세요";

        // 1. 참여자(Junior, Senior) Mock 생성
        Member junior = mock(Member.class);
        Member senior = mock(Member.class);

        // 💡 실제 로직에서 sender.getNickname()과 getId()를 모두 사용하므로 스터빙 유지
        given(junior.getId()).willReturn(10L);
        given(junior.getEmail()).willReturn(senderEmail);
        given(junior.getNickname()).willReturn("주니어닉네임");
        
        given(senior.getEmail()).willReturn("senior@test.com");

        // 2. ChatRoom 생성 (ID 주입)
        ChatRoom chatRoom = ChatRoom.builder()
                .junior(junior)
                .senior(senior)
                .build();
        ReflectionTestUtils.setField(chatRoom, "id", roomId);

        // 3. Mock Repository 설정
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));
        // 서비스 로직이 email로 회원을 찾도록 바뀌었으므로 findByEmail로 스터빙
        given(memberRepository.findByEmail(senderEmail)).willReturn(Optional.of(junior));

        // 4. 저장된 메시지 Mock (로직에서 사용하는 필드만 스터빙)
        ChatMessage mockSavedMessage = mock(ChatMessage.class);
        given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(mockSavedMessage);
        given(mockSavedMessage.getContent()).willReturn(content);

        // when
        // 파라미터로 ID 대신 Email(senderEmail)을 전달
        chatMessageService.sendMessage(roomId, senderEmail, content);

        // then
        //  권한 검증 로직이 정상적으로 호출되었는지 확인
        verify(chatRoomService).verifyParticipant(chatRoom, junior);

        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSendToUser(eq("junior@test.com"), eq("/queue/chat"), any(ChatMessageResponse.class));
        verify(messagingTemplate).convertAndSendToUser(eq("senior@test.com"), eq("/queue/chat"), any(ChatMessageResponse.class));
    }
}