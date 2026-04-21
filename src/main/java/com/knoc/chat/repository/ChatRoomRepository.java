package com.knoc.chat.repository;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    // junior+senior 조합으로 채팅방 조회하는 메서드
    Optional<ChatRoom> findByJuniorAndSenior(Member junior, Member senior);

    // 현재 로그인한 유저가 주니어인지 시니어인지
    List<ChatRoom> findByJuniorOrSenior(Member Junior, Member Senior);
}
