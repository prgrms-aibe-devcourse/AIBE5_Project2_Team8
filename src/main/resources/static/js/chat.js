// ==========================================
// 1. 초기 세팅 및 STOMP 연결 준비
// ==========================================
// 타임리프를 통해 서버에서 직접 넘겨준 채팅방 ID와 JWT 토큰 (없으면 null 처리)
const ROOM_ID = typeof window !== 'undefined' && window.ROOM_ID ? window.ROOM_ID : null;
const JWT_TOKEN = typeof window !== 'undefined' && window.JWT_TOKEN ? window.JWT_TOKEN : null;

const chatContainer = document.getElementById('chat-messages');

// XSS 방어를 위한 특수문자 이스케이프 함수
function escapeHTML(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ==========================================
// 2. DOM 동적 생성: 시스템 메시지 렌더링
// ==========================================
function renderSystemMessage(data) {
    // 채팅창 컨테이너가 없으면 함수 종료 (에러 방지)
    if (!chatContainer) return;

    const systemWrap = document.createElement('div');
    systemWrap.className = 'message-row system';

    // XSS 방어 적용: 서버에서 온 데이터를 안전한 텍스트로 변환 후 줄바꿈(<br>) 처리
    const safeContent = escapeHTML(data.customContent);
    const formattedText = safeContent.replace(/\n/g, '<br>');

    let cardClass = ''; let headerColorClass = ''; let headerIcon = ''; let buttonHtml = '';

    // message_type에 따른 UI 분기
    switch (data.type) {
        case 'PAYMENT_REQUESTED': {
            cardClass = 'type-payment'; headerColorClass = 'text-yellow'; headerIcon = '🪙';
            const amount = data.amount ? data.amount.toLocaleString() : '0';
            buttonHtml = `<button class="sys-action-btn btn-yellow action-pay" data-order-id="${escapeHTML(String(data.referenceId))}">🛡️ 에스크로 안전 결제 ₩${amount}</button>`;
            break;
        }
        case 'PAYMENT_COMPLETED':
        case 'WORKSPACE_READY': {
            cardClass = 'type-review'; headerColorClass = 'text-blue'; headerIcon = '📄';
            buttonHtml = `<button class="sys-action-btn btn-blue action-review" data-room-id="${escapeHTML(String(data.roomId))}">&lt;/&gt; 상세 리뷰 요청서 작성</button>`;
            break;
        }
        case 'REPORT_COMPLETED': {
            cardClass = 'type-review'; headerColorClass = 'text-blue'; headerIcon = '✅';
            buttonHtml = `<button class="sys-action-btn btn-cyan action-confirm" data-report-id="${escapeHTML(String(data.referenceId))}">✔️ 구매 확정 및 리뷰 남기기</button>`;
            break;
        }
        // ROOM_CLOSE 타입 추가
        case 'ROOM_CLOSE': {
            cardClass = 'type-default'; headerColorClass = 'text-gray'; headerIcon = '🔒';
            break;
        }
        default: {
            cardClass = 'type-default'; headerColorClass = 'text-gray'; headerIcon = '🔔';
            break;
        }
    }

    systemWrap.innerHTML = `
        <div class="system-message-card ${cardClass}">
            <div class="sys-header ${headerColorClass}">
                <span style="margin-right: 6px;">${headerIcon}</span> 시스템 알림
            </div>
            <div class="sys-body">${formattedText}</div>
            ${buttonHtml ? `<div class="sys-footer">${buttonHtml}</div>` : ''}
        </div>
    `;

    chatContainer.appendChild(systemWrap);
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

// ==========================================
// 3. 이벤트 바인딩: 액션 버튼 클릭 처리 (이벤트 위임)
// ==========================================
if (chatContainer) {
    chatContainer.addEventListener('click', function(e) {
        const target = e.target.closest('.sys-action-btn');
        if (!target) return;

        if (target.classList.contains('action-pay')) {
            const orderId = target.getAttribute('data-order-id');
            console.log(`[결제 요청] 주문 ID: ${orderId}`);
            // TODO: 결제 모듈 호출 연동
        }
        else if (target.classList.contains('action-review')) {
            const roomId = target.getAttribute('data-room-id');
            console.log(`[리뷰 폼 이동] 채팅방 ID: ${roomId}`);
            // TODO: 리뷰 작성 페이지로 라우팅
        }
        else if (target.classList.contains('action-confirm')) {
            const reportId = target.getAttribute('data-report-id');
            if (confirm("구매를 확정하시겠습니까?\n구매 확정 시 에스크로 대금이 시니어에게 정산됩니다.")) {
                console.log(`[구매 확정 API 호출] 리포트 ID: ${reportId}`);
                // TODO: 서버로 구매 확정 API 호출
            }
        }
    });
}

// ==========================================
// 4. UI 제어: 채팅방 마감 시 입력창 배너로 교체
// ==========================================
function disableChatUI() {
    const inputArea = document.getElementById('dynamic-input-area');
    if (inputArea) {
        // 기존 입력창을 지우고 알약 형태의 배너 삽입
        inputArea.innerHTML = `
            <div class="read-only-banner">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#38bdf8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 8px; vertical-align: middle;">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                    <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>
                <span>멘토링이 완료되어 읽기 전용 상태로 전환되었습니다.</span>
            </div>
        `;
    }
}

// ==========================================
// 5. STOMP 수신: 소켓 연결 및 구독
// ==========================================
if (ROOM_ID && JWT_TOKEN) {
    const socket = new SockJS(`/ws`);
    const stompClient = Stomp.over(socket);
    stompClient.debug = null;

    const connectHeaders = { Authorization: `Bearer ${JWT_TOKEN}` };

    stompClient.connect(connectHeaders, function (frame) {
        console.log('✅ STOMP 서버 연결 완료');

        stompClient.subscribe(`/topic/chat/${ROOM_ID}`, function (message) {
            const data = JSON.parse(message.body);

            // 실시간 마감 이벤트 감지
            if (data.type === 'ROOM_CLOSE') {
                renderSystemMessage(data); // 1. 채팅창에 "마감되었습니다" 시스템 메시지 카드 출력
                disableChatUI();           // 2. 하단 입력창을 알약 배너로 교체

                // 3. 소켓 연결 안전하게 종료
                stompClient.disconnect(function() {
                    console.log("🔒 채팅방 마감: 소켓 연결이 안전하게 종료되었습니다.");
                });
                return; // 이후 로직(일반 렌더링)을 타지 않도록 함수 종료
            }

            // 일반 시스템 메시지 렌더링
            if (data.type !== 'USER') {
                renderSystemMessage(data);
            } else {
                // TODO: 일반 유저 채팅 메시지 렌더링 로직 (추후 구현)
            }
        });
    }, function (error) {
        console.error('❌ STOMP 연결 실패:', error);
    });
} else {
    console.warn("⚠️ [채팅 대기 중] ROOM_ID 또는 JWT_TOKEN이 전달되지 않았습니다.");
}