// ==========================================
// 1. 초기 세팅 및 STOMP 연결 준비
// ==========================================
// 타임리프를 통해 서버에서 넘겨준 채팅방 ID
const ROOM_ID = /*[[${roomId}]]*/ 0;

// 쿠키에서 JWT 토큰 추출
function getCookie(name) {
    let matches = document.cookie.match(new RegExp(
        "(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, '\\$1') + "=([^;]*)"
    ));
    return matches ? decodeURIComponent(matches[1]) : undefined;
}
const JWT_TOKEN = getCookie("accessToken");
const chatContainer = document.getElementById('chat-messages');

// ==========================================
// 2. DOM 동적 생성: 시스템 메시지 렌더링
// ==========================================
function renderSystemMessage(data) {
    const systemWrap = document.createElement('div');
    systemWrap.className = 'message-row system';

    const formattedText = data.customContent.replace(/\n/g, '<br>');

    let cardClass = '';
    let headerColorClass = '';
    let headerIcon = '';
    let buttonHtml = '';

    // message_type에 따른 UI 분기
    switch (data.type) {
        case 'PAYMENT_REQUESTED': {
            cardClass = 'type-payment';
            headerColorClass = 'text-yellow';
            headerIcon = '🪙';
            const amountMatch = data.customContent.match(/([\d,]+)원/);
            const amount = amountMatch ? amountMatch[1] : '';
            buttonHtml = `<button class="sys-action-btn btn-yellow action-pay" data-order-id="${data.referenceId}">🛡️ 에스크로 안전 결제 ₩${amount}</button>`;
            break;
        }
        case 'PAYMENT_COMPLETED':
        case 'WORKSPACE_READY': {
            cardClass = 'type-review';
            headerColorClass = 'text-blue';
            headerIcon = '📄';
            buttonHtml = `<button class="sys-action-btn btn-blue action-review" data-room-id="${data.roomId}">&lt;/&gt; 상세 리뷰 요청서 작성</button>`;
            break;
        }
        case 'REPORT_COMPLETED': {
            cardClass = 'type-review';
            headerColorClass = 'text-blue';
            headerIcon = '✅';
            buttonHtml = `<button class="sys-action-btn btn-cyan action-confirm" data-report-id="${data.referenceId}">✔️ 구매 확정 및 리뷰 남기기</button>`;
            break;
        }
        default: {
            cardClass = 'type-default';
            headerColorClass = 'text-gray';
            headerIcon = '🔔';
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

// ==========================================
// 4. STOMP 수신: 소켓 연결 및 구독
// ==========================================
const socket = new SockJS(`/ws`);
const stompClient = Stomp.over(socket);
stompClient.debug = null;

const connectHeaders = {
    Authorization: `Bearer ${JWT_TOKEN}`
};

stompClient.connect(connectHeaders, function (frame) {
    console.log('✅ STOMP 서버 연결 완료');

    stompClient.subscribe(`/topic/chat/${ROOM_ID}`, function (message) {
        const data = JSON.parse(message.body);

        if (data.type !== 'USER') {
            renderSystemMessage(data); // 시스템 메시지 렌더링 호출
        } else {
            // 일반 유저 채팅 렌더링 영역
        }
    });
}, function (error) {
    console.error('❌ STOMP 연결 실패:', error);
});