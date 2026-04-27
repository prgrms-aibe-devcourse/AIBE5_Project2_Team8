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
import com.knoc.senior.repository.SeniorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true) // 데이터 변경이 없는 조회 메서드가 많으므로 기본값을 readOnly로 설정
@RequiredArgsConstructor
public class ChatRoomService {
    //그 결과를 보고 이미 있으면 예외, 없으면 생성 판단
    private final ChatRoomRepository chatRoomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SeniorProfileRepository seniorProfileRepository;

    // 권한 검증 로직
    public void verifyParticipant(ChatRoom chatRoom, Member currentMember) {
        if(!chatRoom.getJunior().getId().equals(currentMember.getId()) &&
                !chatRoom.getSenior().getId().equals(currentMember.getId()))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    // 최신 메시지 map 생성 로직
    // - 쿼리 1번으로 모든 방의 최신 메시지 조회 (N+1 방지)
    // - 시니어 화면에서는 REVIEW_REQUESTED가 최신인 방만 추가 조회하여 필터링
    private Map<Long, ChatMessage> buildLatestMessages(List<ChatRoom> chatRooms, Member currentMember) {
        List<Long> roomIds = chatRooms.stream()
                .map(ChatRoom::getId)
                .toList();

        Map<Long, ChatMessage> latestMessages = chatMessageRepository
                .findLatestMessagesForRooms(roomIds).stream()
                .collect(Collectors.toMap(m -> m.getChatRoom().getId(), m -> m));

        // 시니어인 경우, REVIEW_REQUESTED가 최신인 방만 추가 조회
        for (ChatRoom chatRoom : chatRooms) {
            ChatMessage latest = latestMessages.get(chatRoom.getId());
            if (latest != null
                    && latest.getMessageType() == MessageType.REVIEW_REQUESTED
                    && chatRoom.getSenior().getId().equals(currentMember.getId())) {
                latestMessages.put(chatRoom.getId(),
                        chatMessageRepository.findFirstByChatRoomAndMessageTypeNotOrderByCreatedAtDesc(
                                chatRoom, MessageType.REVIEW_REQUESTED));
            }
        }

        return latestMessages;
    }

    // 채팅방 목록 조회 로직
    public ChatRoomListDto getChatRoomListDto(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<ChatRoom> chatRooms = chatRoomRepository.findByJuniorOrSenior(member, member);
        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms, member);

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

        messages = new ArrayList<>(messages);
        Collections.reverse(messages);
        Long firstMessageId = messages.isEmpty() ? Long.MAX_VALUE : messages.get(0).getId();
        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms, currentMember);

        // 이 채팅방 시니어의 등록 리뷰 단가 (결제 요청 모달 placeholder용)
        // 프로필 미등록/미설정 시 0 → 프런트에서 기본값으로 처리
        int seniorPricePerReview = seniorProfileRepository.findByMemberId(chatRoom.getSenior().getId())
                .map(p -> p.getPricePerReview())
                .orElse(0);

        return new ChatRoomDetailDto(
                roomId, messages, currentMember.getNickname(),chatRooms,
                chatRoom, firstMessageId, latestMessages, chatRoom.getStatus().name(),  seniorPricePerReview
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
