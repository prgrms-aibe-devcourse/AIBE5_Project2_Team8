package com.knoc.chat.repository;

import com.knoc.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
}
