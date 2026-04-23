package com.knoc.chat;

import com.knoc.auth.jwt.JwtTokenProvider;
import com.knoc.chat.dto.ChatMessageRequest;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private WebSocketStompClient stompClient;
    private StompHeaders stompHeaders;
    private WebSocketHttpHeaders httpHeaders;

    private Long activeRoomId;
    private Long closedRoomId;

    @BeforeEach
    void setup() {
        Member senior = memberRepository.save(Member.builder()
                .email("senior@test.com")
                .password("password")
                .nickname("시니어멘토")
                .role(MemberRole.SENIOR)
                .build());

        Member junior = memberRepository.save(Member.builder()
                .email("junior@test.com")
                .password("password")
                .nickname("주니어")
                .role(MemberRole.USER)
                .build());

        ChatRoom activeRoom = chatRoomRepository.save(ChatRoom.builder()
                .senior(senior)
                .junior(junior)
                .build());
        activeRoomId = activeRoom.getId();

        ChatRoom closedRoom = chatRoomRepository.save(ChatRoom.builder()
                .senior(senior)
                .junior(junior)
                .build());
        closedRoom.close();
        chatRoomRepository.save(closedRoom);
        closedRoomId = closedRoom.getId();

        // SockJS & STOMP 클라이언트 생성
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String validToken = jwtTokenProvider.createAccessToken(senior.getEmail(), "ROLE_SENIOR");

        // 보안(Security) 통과를 위한 헤더 세팅
        stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + validToken);   // STOMP

        httpHeaders = new WebSocketHttpHeaders();
        httpHeaders.add("Cookie", "accessToken=" + validToken);   // HTTP handshake
    }

    private class TestFrameHandler implements StompFrameHandler {
        private final BlockingQueue<Map<String, Object>> queue;
        public TestFrameHandler(BlockingQueue<Map<String, Object>> queue) { this.queue = queue; }

        @Override
        public Type getPayloadType(StompHeaders headers) { return Map.class; }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.offer((Map<String, Object>) payload);
        }
    }

    private String getWebSocketUrl() {
        return String.format("ws://localhost:%d/ws", port);
    }

    @Test
    @DisplayName("[통합] 1:1 큐 구독 후 메시지를 전송하면 정상적으로 수신한다.")
    void stomp_SendMessage_And_Receive_Success() throws Exception {
        BlockingQueue<Map<String, Object>> blockingQueue = new ArrayBlockingQueue<>(1);

        StompSession session = stompClient.connectAsync(
                getWebSocketUrl(),
                httpHeaders,
                stompHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(3, TimeUnit.SECONDS);

        session.subscribe("/user/queue/chat", new TestFrameHandler(blockingQueue));

        // 구독이 서버에 확실히 등록되도록 1초 대기
        Thread.sleep(1000);

        ChatMessageRequest request = new ChatMessageRequest("통합 테스트 메시지입니다.");
        session.send("/app/" + activeRoomId + "/send", request);

        Map<String, Object> response = blockingQueue.poll(3, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.get("content").toString()).isEqualTo("통합 테스트 메시지입니다.");
    }

    @Test
    @DisplayName("[통합] 서버에서 시스템 이벤트를 발행하면 구독 중인 클라이언트로 실시간 전송된다.")
    void stomp_Receive_System_Event_Success() throws Exception {
        BlockingQueue<Map<String, Object>> blockingQueue = new ArrayBlockingQueue<>(1);

        StompSession session = stompClient.connectAsync(
                getWebSocketUrl(),
                httpHeaders,
                stompHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(3, TimeUnit.SECONDS);

        session.subscribe("/user/queue/chat", new TestFrameHandler(blockingQueue));

        Thread.sleep(1000);

        eventPublisher.publishEvent(new ChatSystemEvent(
                activeRoomId,
                MessageType.PAYMENT_COMPLETED,
                "결제가 완료되었습니다.",
                999L
        ));

        Map<String, Object> response = blockingQueue.poll(3, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.get("messageType").toString()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(response.get("content").toString()).contains("결제가 완료");
    }

    @Test
    @DisplayName("[통합] 마감된 채팅방에 메시지를 전송하면 서버가 거부한다 (수신되지 않음).")
    void stomp_SendMessage_To_ClosedRoom_Fail() throws Exception {
        BlockingQueue<Map<String, Object>> blockingQueue = new ArrayBlockingQueue<>(1);

        StompSession session = stompClient.connectAsync(
                getWebSocketUrl(),
                httpHeaders,
                stompHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(3, TimeUnit.SECONDS);

        session.subscribe("/user/queue/chat", new TestFrameHandler(blockingQueue));

        Thread.sleep(500);

        ChatMessageRequest request = new ChatMessageRequest("마감된 방에 몰래 보내기");
        session.send("/app/" + closedRoomId + "/send", request);

        Map<String, Object> response = blockingQueue.poll(2, TimeUnit.SECONDS);
        assertThat(response).isNull();
    }
}