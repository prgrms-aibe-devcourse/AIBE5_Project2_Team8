package com.knoc.chat.service;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatRoomService {
    //그 결과를 보고 이미 있으면 예외, 없으면 생성 판단
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;

    public ChatRoom createChatRoom(String email, Long seniorId) {

        Member junior = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member senior = memberRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Optional<ChatRoom> existing =  chatRoomRepository.findByJuniorAndSenior(junior, senior);
        if(existing.isPresent()) {
            return existing.get();
        }

        ChatRoom newChatRoom = ChatRoom.builder()
                .junior(junior)
                .senior(senior)
                .build();



        chatRoomRepository.save(newChatRoom);
        return newChatRoom;
    }
}
