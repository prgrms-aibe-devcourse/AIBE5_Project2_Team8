// ==========================================
// 1. 초기 세팅 및 변수 준비 (HTML에서 주입받음)
// ==========================================
const ROOM_ID = window.ROOM_ID ?? null;
const CURRENT_NICKNAME = window.CURRENT_NICKNAME ?? null;
const ROOM_STATUS = window.ROOM_STATUS ?? 'ACTIVE';
// 현재 사용자가 이 채팅방의 시니어인지 (PAYMENT_REQUESTED 결제 버튼 노출 분기용)
const IS_SENIOR = window.IS_SENIOR === true;
// 주니어 ID (시니어 전용 '결제 요청하기' 모달에서 /orders/request 호출 시 사용)
const JUNIOR_ID = window.JUNIOR_ID ?? null;
// 초기 로딩 메시지들의 가장 오래된 id (위로 스크롤해 과거 메시지를 불러올 때 커서로 사용)
const FIRST_MESSAGE_ID = window.FIRST_MESSAGE_ID ?? null;
// 해당 채팅방 시니어의 등록 리뷰 단가. 결제 요청 모달 placeholder 기본값으로 사용.
const SENIOR_PRICE_PER_REVIEW = window.SENIOR_PRICE_PER_REVIEW ?? 0;

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

    // String()으로 감싸 IDE의 type narrowing을 차단한다.
    // (호출부의 `type !== 'USER'` 같은 조건으로 인해 WebStorm이 일부 case를 도달 불가로 오판하는 것을 방지)
    const msgType = String(data.messageType || data.type || '');

    switch (msgType) {
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

    // 인증은 Spring Security 세션 쿠키 기반이므로 별도 헤더가 필요 없다.
    // 향후 JWT 도입 시 여기서 Authorization 헤더를 주입한다.
    const connectHeaders = {};

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
            const type = data.messageType || data.type;

            // 실시간 마감 이벤트 감지
            if (type === 'ROOM_CLOSE') {
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

            // 결제 요청이 발행되면(이벤트 수신) 헤더의 '결제 요청하기' 버튼을 즉시 숨긴다.
            // - 시니어 본인: 자신이 방금 요청한 결과로 버튼이 사라짐
            // - 주니어: 헤더에 버튼 자체가 렌더되지 않지만 방어적으로 동일 처리
            if (type === 'PAYMENT_REQUESTED') {
                hideRequestPaymentButton();
            }

            // 메시지 타입 분기 (유저 vs 시스템)
            if (type === 'USER') {
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
                if (!messages || messages.length === 0) {
                    paginationCursor = null;
                    return;
                }

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
                if (messages.length < 20) paginationCursor = null;
            })
            .catch(err => console.error('과거 메시지 로딩 실패:', err))
            .finally(() => { isLoadingOlder = false; });
    });
}

// ==========================================
// 8. 시니어 '결제 요청하기' 버튼 / 모달 제어
// ==========================================

// 금액 하한/상한. 서버 정책과 일치시킬 것 (서버가 1차 검증, 프런트는 UX용 사전 필터).
const MIN_PAYMENT_AMOUNT = 1000;
const MAX_PAYMENT_AMOUNT = 10_000_000;

// 헤더의 '결제 요청하기' 버튼 숨기기.
// PAYMENT_REQUESTED 이벤트 수신 또는 API 응답 성공 시 호출되어, 한 채팅방당 한 번만 결제 요청하도록 강제.
function hideRequestPaymentButton() {
    const btn = document.getElementById('requestPaymentBtn');
    if (btn) btn.style.display = 'none';
}

// --- 모달 open / close ---

function openPaymentRequestModal() {
    const modal = document.getElementById('paymentRequestModal');
    if (!modal) return;

    const input = document.getElementById('paymentAmountInput');
    if (input) {
        input.value = '';
        // 시니어가 프로필에 등록한 리뷰 단가를 placeholder로 제시 (0이면 '0')
        input.placeholder = SENIOR_PRICE_PER_REVIEW > 0
            ? formatAmountWithComma(SENIOR_PRICE_PER_REVIEW)
            : '0';
    }
    setPaymentModalError('');
    setPaymentSubmitLoading(false);

    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';

    // 모달 애니메이션 직후 포커스 (즉시 포커스하면 iOS/Safari에서 스크롤 튐)
    setTimeout(() => { if (input) input.focus(); }, 50);
}

function closePaymentRequestModal() {
    const modal = document.getElementById('paymentRequestModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
}

// --- 금액 유틸 ---

// 사용자 입력 문자열 → 숫자. 콤마/공백/기타 문자 제거. 빈값이면 null.
function parseAmountInput(value) {
    const digits = String(value || '').replace(/[^0-9]/g, '');
    if (!digits) return null;
    return parseInt(digits, 10);
}

function formatAmountWithComma(num) {
    if (num == null || isNaN(num)) return '';
    return Number(num).toLocaleString();
}

// --- 에러/로딩 상태 ---

function setPaymentModalError(msg) {
    const errEl = document.getElementById('paymentModalError');
    if (!errEl) return;
    if (msg) {
        errEl.textContent = msg;
        errEl.style.display = 'block';
    } else {
        errEl.textContent = '';
        errEl.style.display = 'none';
    }
}

function setPaymentSubmitLoading(loading) {
    const submitBtn = document.getElementById('paymentSubmitBtn');
    const cancelBtn = document.getElementById('paymentCancelBtn');
    if (submitBtn) {
        submitBtn.disabled = !!loading;
        submitBtn.textContent = loading ? '요청 중...' : '결제 요청 보내기';
    }
    if (cancelBtn) cancelBtn.disabled = !!loading;
}

// --- Idempotency-Key 생성 ---
// crypto.randomUUID는 최신 브라우저(2021~)에서 지원. HTTPS/localhost에서만 사용 가능.
// 그 외 환경을 위한 RFC4122 v4 UUID fallback 제공.
function generateIdempotencyKey() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

// --- API 호출: POST /orders/request ---

async function submitPaymentRequest() {
    const input = document.getElementById('paymentAmountInput');
    const amount = parseAmountInput(input && input.value);

    // 1차 유효성 검증
    if (amount == null) return setPaymentModalError('금액을 입력해 주세요.');
    if (amount < MIN_PAYMENT_AMOUNT) {
        return setPaymentModalError(`최소 ${formatAmountWithComma(MIN_PAYMENT_AMOUNT)}원부터 요청할 수 있어요.`);
    }
    if (amount > MAX_PAYMENT_AMOUNT) {
        return setPaymentModalError(`최대 ${formatAmountWithComma(MAX_PAYMENT_AMOUNT)}원까지 요청할 수 있어요.`);
    }
    if (!ROOM_ID || !JUNIOR_ID) {
        return setPaymentModalError('채팅방 정보가 없어요. 페이지를 새로고침해 주세요.');
    }

    setPaymentModalError('');
    setPaymentSubmitLoading(true);

    try {
        const res = await fetch('/orders/request', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': generateIdempotencyKey(),
            },
            credentials: 'same-origin', // JWT 쿠키(accessToken) 자동 포함
            body: JSON.stringify({
                chatRoomId: ROOM_ID,
                juniorId: JUNIOR_ID,
                amount: amount,
            }),
        });

        if (!res.ok) {
            const text = await res.text().catch(() => '');
            throw new Error(text || `서버 응답 오류 (${res.status})`);
        }

        // 낙관적 처리: PAYMENT_REQUESTED WebSocket 이벤트 도착 전에 버튼/모달 선제 정리.
        // 시스템 메시지 렌더링은 구독 핸들러가 담당하므로 여기서는 건드리지 않는다.
        hideRequestPaymentButton();
        closePaymentRequestModal();
    } catch (err) {
        console.error('[결제 요청 실패]', err);
        setPaymentModalError('결제 요청에 실패했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
        setPaymentSubmitLoading(false);
    }
}

// --- 이벤트 바인딩 (IIFE로 스코프 격리) ---

(function bindPaymentModalEvents() {
    const modal = document.getElementById('paymentRequestModal');
    if (!modal) return; // 주니어 화면 등 모달이 없는 경우

    // 닫기 트리거: [data-pmt-close] (백드롭 + X 버튼)
    modal.querySelectorAll('[data-pmt-close]').forEach((el) => {
        el.addEventListener('click', closePaymentRequestModal);
    });

    const cancelBtn = document.getElementById('paymentCancelBtn');
    if (cancelBtn) cancelBtn.addEventListener('click', closePaymentRequestModal);

    const submitBtn = document.getElementById('paymentSubmitBtn');
    if (submitBtn) submitBtn.addEventListener('click', submitPaymentRequest);

    // ESC로 닫기 (모달이 열려있을 때만)
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && modal.classList.contains('is-open')) {
            closePaymentRequestModal();
        }
    });

    // 금액 입력: 실시간 콤마 포맷 + Enter 제출 (IME 조합 중 제외)
    const input = document.getElementById('paymentAmountInput');
    if (input) {
        input.addEventListener('input', function () {
            const num = parseAmountInput(input.value);
            input.value = num == null ? '' : formatAmountWithComma(num);
        });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.isComposing) {
                e.preventDefault();
                submitPaymentRequest();
            }
        });
    }
})();