package com.knoc.chat.service;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatRoomService {
    //그 결과를 보고 이미 있으면 예외, 없으면 생성 판단
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberRepository memberRepository;

    public ChatRoom createChatRoom(String email, Long seniorId) {

        Member junior = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member senior = memberRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Optional<ChatRoom> existing =  chatRoomRepository.findByJuniorAndSenior(junior, senior);
        if(existing.isPresent()) {
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_EXISTS);
        }

        ChatRoom newChatRoom = ChatRoom.builder()
                .junior(junior)
                .senior(senior)
                .build();



        chatRoomRepository.save(newChatRoom);
        return newChatRoom;
    }

    @Transactional
    public void closeChatRoom(Long roomId, Long seniorId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        if(!chatRoom.getSenior().getId().equals(seniorId)) {
            throw new BusinessException(ErrorCode.NOT_SENIOR_IN_ROOM);
        }
        chatRoom.close();
        eventPublisher.publishEvent(new ChatSystemEvent(
                roomId,
                MessageType.ROOM_CLOSE,
                MessageType.ROOM_CLOSE.getTemplate(),
                null
        ));
    }
    @Transactional(readOnly = true)
    public ChatRoom getRoom(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
    }
}
