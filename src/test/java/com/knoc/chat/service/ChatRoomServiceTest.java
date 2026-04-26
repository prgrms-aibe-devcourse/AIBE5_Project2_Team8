package com.knoc.chat.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatRoomStatus;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatRoomServiceTest {

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("채팅방 마감이 요청되면 상태가 CLOSED 변경되고 이벤트가 발행된다.")
    void closeChatRoom_Success() {
        // given
        Long roomId = 1L;
        Long seniorId = 10L;

        Member senior = mock(Member.class);
        given(senior.getId()).willReturn(seniorId);

        ChatRoom chatRoom = ChatRoom.builder()
                .junior(mock(Member.class))
                .senior(senior)
                .build();

        // 2. id는 빌더에 없으므로 ReflectionTestUtils로 강제 주입
        ReflectionTestUtils.setField(chatRoom, "id", roomId);
        // 기본값은 ACTIVE이므로 별도 세팅 불필요 (엔티티 생성자에서 세팅됨)

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));

        // when
        chatRoomService.closeChatRoom(roomId, seniorId);

        // then
        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.CLOSED); // close() 메서드 작동 확인
        assertThat(chatRoom.getClosedAt()).isNotNull(); // closedAt 세팅 확인
        verify(eventPublisher).publishEvent(any(ChatSystemEvent.class)); // 이벤트 발행 확인
    }

    @Test
    @DisplayName("담당 시니어가 아닌 다른 사람이 마감을 요청하면 예외가 발생한다.")
    void closeChatRoom_Fail_Unauthorized() {
        // given
        Long roomId = 1L;
        Long seniorId = 10L;
        Long hackerId = 999L;

        Member senior = mock(Member.class);
        given(senior.getId()).willReturn(seniorId);

        ChatRoom chatRoom = ChatRoom.builder()
                .junior(mock(Member.class))
                .senior(senior)
                .build();

        ReflectionTestUtils.setField(chatRoom, "id", roomId);

        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(chatRoom));

        // when & then
        assertThrows(BusinessException.class, () -> {
            chatRoomService.closeChatRoom(roomId, hackerId);
        });
    }

    @Test
    @DisplayName("채팅방 생성 시 기존 방이 없으면 새로운 방을 생성하여 저장한다.")
    void createChatRoom_Success_NewRoom() {
        // given
        String email = "junior@test.com";
        Long seniorId = 10L;

        Member junior = mock(Member.class);
        Member senior = mock(Member.class);

        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));
        given(memberRepository.findById(seniorId)).willReturn(Optional.of(senior));
        given(chatRoomRepository.findByJuniorAndSenior(junior, senior)).willReturn(Optional.empty());

        // when
        ChatRoom newRoom = chatRoomService.createChatRoom(email, seniorId);

        // then
        assertThat(newRoom).isNotNull();
        verify(chatRoomRepository, times(1)).save(any(ChatRoom.class));
        verify(eventPublisher, never()).publishEvent(any(ChatSystemEvent.class));
    }

    @Test
    @DisplayName("채팅방 생성 시 이미 종료된(CLOSED) 방이 존재하면, 방을 다시 열고(OPEN) 시스템 이벤트를 발행한다.")
    void createChatRoom_Success_ReopenClosedRoom() {
        // given
        String email = "junior@test.com";
        Long seniorId = 10L;

        Member junior = mock(Member.class);
        Member senior = mock(Member.class);

        ChatRoom closedRoom = ChatRoom.builder().junior(junior).senior(senior).build();
        ReflectionTestUtils.setField(closedRoom, "id", 1L);
        closedRoom.close(); // 상태를 CLOSED로 만듦

        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));
        given(memberRepository.findById(seniorId)).willReturn(Optional.of(senior));
        given(chatRoomRepository.findByJuniorAndSenior(junior, senior)).willReturn(Optional.of(closedRoom));

        // when
        ChatRoom returnedRoom = chatRoomService.createChatRoom(email, seniorId);

        // then
        assertThat(returnedRoom.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE); // 다시 OPEN(ACTIVE) 상태가 되어야 함
        verify(chatRoomRepository, never()).save(any(ChatRoom.class)); // 이미 있는 방이므로 새로 save하면 안 됨

        // ROOM_REOPEN 이벤트 발행 검증

        ArgumentCaptor<ChatSystemEvent> eventCaptor = ArgumentCaptor.forClass(ChatSystemEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        ChatSystemEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.type()).isEqualTo(MessageType.ROOM_REOPEN);
    }
}