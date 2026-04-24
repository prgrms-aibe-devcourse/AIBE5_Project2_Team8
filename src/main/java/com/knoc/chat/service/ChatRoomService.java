package com.knoc.chat.service;

import com.knoc.chat.dto.ChatRoomDetailDto;
import com.knoc.chat.dto.ChatRoomListDto;
import com.knoc.chat.entity.*;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.sun.source.tree.MemberReferenceTree;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true) // 데이터 변경이 없는 조회 메서드가 많으므로 기본값을 readOnly로 설정
@RequiredArgsConstructor
public class ChatRoomService {
    //그 결과를 보고 이미 있으면 예외, 없으면 생성 판단
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 권한 검증 로직
    public void verifyParticipant(ChatRoom chatRoom, Member currentMember) {
        if(!chatRoom.getJunior().getId().equals(currentMember.getId()) &&
                !chatRoom.getSenior().getId().equals(currentMember.getId()))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 최신 메시지 map 생성 로직
    private Map<Long, ChatMessage> buildLatestMessages(List<ChatRoom> chatRooms) {
        Map<Long, ChatMessage> lastestMessages = new HashMap<>();
        for(ChatRoom chatRoom : chatRooms) {
            lastestMessages.put(chatRoom.getId(), chatMessageRepository.findFirstByChatRoomOrderByCreatedAtDesc(chatRoom));
        }
        return lastestMessages;
    }

    // 채팅방 목록 조회 로직
    public ChatRoomListDto getChatRoomListDto(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<ChatRoom> chatRooms = chatRoomRepository.findByJuniorOrSenior(member, member);
        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms);

        return new ChatRoomListDto(chatRooms, latestMessages, member.getNickname());
    }

    // 채팅방 상세 조회 로직
    public ChatRoomDetailDto getRoomDetailInfo(Long roomId, String email) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        Member currentMember = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        verifyParticipant(chatRoom, currentMember);
        List<ChatRoom> chatRooms = chatRoomRepository.findByJuniorOrSenior(currentMember, currentMember);
        List<ChatMessage> messages = chatMessageRepository
                .findByChatRoomAndIdLessThanOrderByIdDesc(chatRoom, Long.MAX_VALUE, PageRequest.of(0, 20));
        Collections.reverse(messages);
        Long firstMessageId = messages.isEmpty() ? Long.MAX_VALUE : messages.get(0).getId();
        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms);

        return new ChatRoomDetailDto(
                roomId, messages, currentMember.getNickname(),chatRooms,
                chatRoom, firstMessageId, latestMessages, chatRoom.getStatus().name()
        );

    }

    @Transactional
    public ChatRoom createChatRoom(String email, Long seniorId) {

        Member junior = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member senior = memberRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Optional<ChatRoom> existing =  chatRoomRepository.findByJuniorAndSenior(junior, senior);
        if(existing.isPresent()) {
            ChatRoom chatRoom = existing.get();

            if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
                chatRoom.reopen();

                eventPublisher.publishEvent(new ChatSystemEvent(
                        chatRoom.getId(),
                        MessageType.ROOM_REOPEN,
                        MessageType.ROOM_REOPEN.getTemplate(),
                        null
                ));
            }

            return chatRoom;
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
