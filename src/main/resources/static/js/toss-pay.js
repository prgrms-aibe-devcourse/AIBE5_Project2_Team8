// 채팅 결제 버튼 등에서 재사용할 수 있도록 `window.startTossPayment(params)`를 제공.
// 아래 토스페이먼츠 v2 standard SDK 스크립트가 먼저 로드되어 있어야 함.
// <script src="https://js.tosspayments.com/v2/standard"></script>

// 이 파일은 "정적 JS"이므로(Thymeleaf 인라이닝 불가) 아래 전역 값 주입은
// 반드시 페이지 템플릿(예: chatrooms.html)에서 수행해야 합니다.
// window.__TOSS_CLIENT_KEY__ = /*[[${tossClientKey}]]*/ "";
// window.__TOSS_SUCCESS_PATH__ = "/orders/payment/toss/success";
// window.__TOSS_FAIL_PATH__ = "/orders/payment/toss/fail";

(function () {
  function getClientKey() {
    return String(window.__TOSS_CLIENT_KEY__ || "");
  }

  function guestCustomerKey() {
    var k = sessionStorage.getItem("tossCustomerKey");
    if (!k) {
      k = "guest_" + crypto.randomUUID();
      sessionStorage.setItem("tossCustomerKey", k);
    }
    return k;
  }

  // 채팅 연동 시: 토스에 넘기는 orderId는 DB 주문의 orderNumber(예: ORD-...)와 동일해야 승인 후 Knoc 주문이 PAID로 반영됩니다.
  function randomOrderId() {
    return (
      "TEST-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10)
    );
  }

  function getSuccessUrl() {
    var path = String(window.__TOSS_SUCCESS_PATH__ || "/orders/payment/toss/success");
    return window.location.origin + path;
  }

  function getFailUrl() {
    var path = String(window.__TOSS_FAIL_PATH__ || "/orders/payment/toss/fail");
    return window.location.origin + path;
  }

  window.startTossPayment = async function startTossPayment(params) {
    var clientKey = getClientKey();
    if (!clientKey) {
      throw new Error("Toss clientKey가 설정되어 있지 않습니다.");
    }
    if (typeof TossPayments !== "function") {
      throw new Error("TossPayments SDK가 로드되지 않았습니다.");
    }

    var amount = Number(params && params.amount != null ? params.amount : 15000);
    var orderName = String(
      params && params.orderName != null ? params.orderName : "Knoc 테스트 멘토링 결제"
    );
    var customerName = String(
      params && params.customerName != null ? params.customerName : "테스트"
    );
    var orderId = String(params && params.orderId != null ? params.orderId : "");
    if (!orderId) orderId = randomOrderId();

    var tossPayments = TossPayments(clientKey);
    var payment = tossPayments.payment({ customerKey: guestCustomerKey() });

    await payment.requestPayment({
      method: "CARD",
      amount: { currency: "KRW", value: amount },
      orderId: orderId,
      orderName: orderName,
      successUrl: getSuccessUrl(),
      failUrl: getFailUrl(),
      customerName: customerName,
    });
  };

  // NOTE: 화면별 버튼 연결은 각 페이지 스크립트(chat.js 등)에서 수행합니다.
})();

