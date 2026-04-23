// ==========================================
// 1. 초기 세팅 및 변수 준비 (HTML에서 주입받음)
// ==========================================
const ROOM_ID = typeof window !== 'undefined' && window.ROOM_ID ? window.ROOM_ID : null;
const JWT_TOKEN = typeof window !== 'undefined' && window.JWT_TOKEN ? window.JWT_TOKEN : null;
const CURRENT_NICKNAME = typeof window !== 'undefined' && window.CURRENT_NICKNAME ? window.CURRENT_NICKNAME : null;
const ROOM_STATUS = typeof window !== 'undefined' && window.ROOM_STATUS ? window.ROOM_STATUS : 'ACTIVE';
// 현재 사용자가 이 채팅방의 시니어인지 (PAYMENT_REQUESTED 결제 버튼 노출 분기용)
const IS_SENIOR = typeof window !== 'undefined' && window.IS_SENIOR === true;
// 주니어 ID (시니어 전용 '결제 요청하기' 모달에서 /orders/request 호출 시 사용)
const JUNIOR_ID = typeof window !== 'undefined' && window.JUNIOR_ID ? window.JUNIOR_ID : null;
// 초기 로딩 메시지들의 가장 오래된 id (위로 스크롤해 과거 메시지를 불러올 때 커서로 사용)
const FIRST_MESSAGE_ID = typeof window !== 'undefined' && window.FIRST_MESSAGE_ID ? window.FIRST_MESSAGE_ID : null;

const chatContainer = document.getElementById('messageList');
let stompClient = null;

// XSS 방어 함수
function escapeHTML(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// 스크롤 맨 아래로 이동
function scrollToBottom() {
    if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;
}

// 시간 포맷 (HH:mm)
function formatTime(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
}

// ==========================================
// 2. DOM 렌더링: 시스템 메시지 카드
// ==========================================
function renderSystemMessage(data, options = {}) {
    if (!chatContainer) return;

    const systemWrap = document.createElement('div');
    systemWrap.className = 'message-row system';

    const safeContent = escapeHTML(data.content || data.customContent);
    const formattedText = safeContent.replace(/\n/g, '<br>');

    let cardClass = ''; let headerColorClass = ''; let headerIcon = ''; let buttonHtml = '';

    switch (data.messageType || data.type) {
        case 'PAYMENT_REQUESTED': {
            cardClass = 'type-payment'; headerColorClass = 'text-yellow'; headerIcon = '🪙';
            // 결제 버튼은 주니어에게만 노출 (결제 행위자는 주니어)
            if (!IS_SENIOR && data.referenceId) {
                const amount = data.amount ? data.amount.toLocaleString() : '0';
                buttonHtml = `<button class="sys-action-btn btn-yellow action-pay" data-order-id="${escapeHTML(String(data.referenceId))}">🛡️ 에스크로 안전 결제 ₩${amount}</button>`;
            }
            break;
        }
        case 'PAYMENT_COMPLETED':
        case 'WORKSPACE_READY': {
            cardClass = 'type-review'; headerColorClass = 'text-blue'; headerIcon = '📄';
            buttonHtml = `<button class="sys-action-btn btn-blue action-review" data-room-id="${escapeHTML(String(ROOM_ID))}">&lt;/&gt; 상세 리뷰 요청서 작성</button>`;
            break;
        }
        case 'REPORT_COMPLETED': {
            cardClass = 'type-review'; headerColorClass = 'text-blue'; headerIcon = '✅';
            buttonHtml = `<button class="sys-action-btn btn-cyan action-confirm" data-report-id="${escapeHTML(String(data.referenceId))}">✔️ 구매 확정 및 리뷰 남기기</button>`;
            break;
        }
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

    if (options.prepend) {
        chatContainer.prepend(systemWrap);
    } else {
        chatContainer.appendChild(systemWrap);
        scrollToBottom();
    }
}

// ==========================================
// 3. DOM 렌더링: 일반 유저 메시지
// ==========================================
function renderUserMessage(msg, options = {}) {
    if (!chatContainer) return;

    const wrapper = document.createElement('div');
    const isMine = msg.senderNickname === CURRENT_NICKNAME;
    wrapper.className = isMine ? 'msg-right' : 'msg-left';

    const sender = document.createElement('div');
    sender.className = 'msg-sender';
    sender.textContent = msg.senderNickname;

    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    bubble.textContent = msg.content;

    const time = document.createElement('div');
    time.className = 'msg-time';
    time.textContent = formatTime(msg.createdAt);

    wrapper.appendChild(sender);
    wrapper.appendChild(bubble);
    wrapper.appendChild(time);

    if (options.prepend) {
        chatContainer.prepend(wrapper);
    } else {
        chatContainer.appendChild(wrapper);
        updateSidebarPreview(msg.content);
        scrollToBottom();
    }
}

// 사이드바 미리보기 실시간 업데이트
function updateSidebarPreview(content) {
    const roomItem = document.querySelector(`.room-item[data-room-id="${ROOM_ID}"]`);
    if (!roomItem) return;
    const preview = roomItem.querySelector('.room-preview');
    if (preview) preview.textContent = content;
    const timeEl = roomItem.querySelector('.room-time');
    if (timeEl) {
        const now = new Date();
        timeEl.textContent = (now.getMonth()+1).toString().padStart(2,'0') + '/' + now.getDate().toString().padStart(2,'0');
    }
}

// 사이드바 토글 (모바일 대응)
function toggleSidebar() {
    const sidebar = document.getElementById('chatSidebar');
    const openBtn = document.getElementById('sidebarOpenBtn');
    if(sidebar) sidebar.classList.toggle('collapsed');
    if (openBtn) openBtn.classList.toggle('visible');
}

// ==========================================
// 4. UI 제어: 채팅방 마감 (Read-Only) 전환
// ==========================================
function disableChatUI() {
    const inputArea = document.getElementById('inputArea');
    const readonlyBanner = document.getElementById('readonlyBanner');

    if (inputArea && readonlyBanner) {
        inputArea.style.display = 'none';
        readonlyBanner.style.display = 'flex';
    }
}

// ==========================================
// 5. STOMP 연결 및 구독 로직 (1:1 Queue)
// ==========================================
if (ROOM_ID && ROOM_STATUS !== 'CLOSED') {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    const connectHeaders = JWT_TOKEN ? { Authorization: `Bearer ${JWT_TOKEN}` } : {};

    stompClient.connect(connectHeaders, function () {
        console.log('✅ STOMP 서버 연결 완료');
        const statusEl = document.getElementById('connectionStatus');
        if(statusEl) {
            statusEl.textContent = '연결됨';
            statusEl.className = 'connection-status status-connected';
        }

        // 💡 1:1 큐 구독 방식으로 통신
        stompClient.subscribe('/user/queue/chat', function (message) {
            const data = JSON.parse(message.body);

            // 실시간 마감 이벤트 감지
            if (data.messageType === 'ROOM_CLOSE' || data.type === 'ROOM_CLOSE') {
                renderSystemMessage(data);
                disableChatUI();

                stompClient.disconnect(function() {
                    console.log("🔒 채팅방 마감: 소켓 연결이 안전하게 종료되었습니다.");
                    if(statusEl) {
                        statusEl.textContent = '마감됨';
                        statusEl.className = 'connection-status status-disconnected';
                    }
                });
                return;
            }

            // 메시지 타입 분기 (유저 vs 시스템)
            if (data.messageType === 'USER' || data.type === 'USER') {
                renderUserMessage(data);
            } else {
                renderSystemMessage(data);
            }
        });

        // 페이지 진입 시 스크롤 하단 고정
        setTimeout(scrollToBottom, 100);

    }, function (error) {
        console.error('❌ STOMP 연결 실패:', error);
        const statusEl = document.getElementById('connectionStatus');
        if (statusEl) {
            statusEl.textContent = '연결 끊김';
            statusEl.className = 'connection-status status-disconnected';
        }
    });
} else if (ROOM_STATUS === 'CLOSED') {
    console.warn("🔒 이미 마감된 채팅방입니다. 소켓 연결을 차단합니다.");
    setTimeout(scrollToBottom, 100);
}

// ==========================================
// 6. 메시지 전송 및 버튼 액션 이벤트
// ==========================================
function sendMessage() {
    const input = document.getElementById('messageInput');
    const content = input ? input.value.trim() : '';
    if (!content || !stompClient) return;

    stompClient.send('/app/' + ROOM_ID + '/send', {}, JSON.stringify({ content: content }));
    input.value = '';
}

// 엔터키 전송 처리
const messageInput = document.getElementById('messageInput');
if (messageInput) {
    messageInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.isComposing) sendMessage();
    });
}

// 시스템 알림 버튼 클릭 이벤트 (이벤트 위임)
if (chatContainer) {
    chatContainer.addEventListener('click', function(e) {
        const target = e.target.closest('.sys-action-btn');
        if (!target) return;

        if (target.classList.contains('action-pay')) {
            const orderId = target.getAttribute('data-order-id');
            console.log(`[결제 요청] 주문 ID: ${orderId}`);
            // TODO: 결제 모듈 호출
        } else if (target.classList.contains('action-review')) {
            const roomId = target.getAttribute('data-room-id');
            console.log(`[리뷰 폼 이동] 방 번호: ${roomId}`);
            // TODO: 리뷰 작성 페이지로 라우팅
        } else if (target.classList.contains('action-confirm')) {
            const reportId = target.getAttribute('data-report-id');
            if (confirm("구매를 확정하시겠습니까?\n구매 확정 시 에스크로 대금이 시니어에게 정산됩니다.")) {
                console.log(`[구매 확정 API 호출] 리포트 ID: ${reportId}`);
                // TODO: 서버로 구매 확정 API 호출
            }
        }
    });
}

// ==========================================
// 7. 페이지네이션: 스크롤 업 시 과거 메시지 로딩
// ==========================================
let paginationCursor = FIRST_MESSAGE_ID;
let isLoadingOlder = false;

if (chatContainer && ROOM_ID) {
    chatContainer.addEventListener('scroll', function () {
        if (chatContainer.scrollTop !== 0 || isLoadingOlder || !paginationCursor) return;

        isLoadingOlder = true;
        fetch(`/chat/${ROOM_ID}/messages?before=${paginationCursor}`)
            .then(res => res.json())
            .then(messages => {
                if (!messages || messages.length === 0) return;

                const prevHeight = chatContainer.scrollHeight;

                // API는 오래된 → 최신 순으로 반환한다.
                // prepend를 반복하면 나중에 prepend한 것이 위로 가므로,
                // 최신 → 오래된 순(역순)으로 prepend 해야 DOM상 오래된 메시지가 가장 위에 온다.
                for (let i = messages.length - 1; i >= 0; i--) {
                    const msg = messages[i];
                    const type = msg.messageType || msg.type;
                    if (type === 'USER') {
                        renderUserMessage(msg, { prepend: true });
                    } else {
                        renderSystemMessage(msg, { prepend: true });
                    }
                }

                // 스크롤 위치 복원 (사용자가 보던 메시지가 계속 같은 위치에 있도록)
                chatContainer.scrollTop = chatContainer.scrollHeight - prevHeight;

                // 다음 페이지네이션 커서: 이번 배치의 가장 오래된 메시지 id
                paginationCursor = messages[0].id;
            })
            .catch(err => console.error('과거 메시지 로딩 실패:', err))
            .finally(() => { isLoadingOlder = false; });
    });
}