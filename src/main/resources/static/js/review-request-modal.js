// 리뷰 요청서 모달 공용 컨트롤러 (create=POST, edit=PATCH)
// - UI 마크업: templates/chat/fragments/review-request-modal.html
// - 스타일:    /css/review-request-modal.css
(function () {
  function $(id) { return document.getElementById(id); }

  function normalizeStr(v) { return String(v || '').trim(); }

  function setError(msg) {
    const errEl = $('reviewRequestError');
    if (!errEl) return;
    if (msg) {
      errEl.textContent = msg;
      errEl.style.display = 'block';
    } else {
      errEl.textContent = '';
      errEl.style.display = 'none';
    }
  }

  function isValidGithubPrUrl(url) {
    const value = normalizeStr(url);
    if (!value) return false;
    return /^https?:\/\/github\.com\/[^/]+\/[^/]+\/pull\/\d+(?:[/?#].*)?$/i.test(value);
  }

  function setLoading(loading, defaultText) {
    const btn = $('reviewRequestSubmitBtn');
    if (!btn) return;
    btn.disabled = !!loading;
    if (!btn.dataset.originalText) btn.dataset.originalText = btn.textContent || defaultText || '';
    btn.textContent = loading ? '제출 중...' : (btn.dataset.originalText || defaultText || btn.textContent);
  }

  function openModal() {
    const modal = $('reviewRequestModal');
    if (!modal) return;
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }

  function closeModal() {
    const modal = $('reviewRequestModal');
    if (!modal) return;
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
    setError('');
    setLoading(false);
  }

  function bindCloseOnce() {
    const modal = $('reviewRequestModal');
    if (!modal) return;
    if (modal.dataset.boundClose === 'true') return;
    modal.dataset.boundClose = 'true';

    modal.querySelectorAll('[data-review-close]').forEach(function (el) {
      el.addEventListener('click', function () { closeModal(); });
    });

    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape') return;
      const m = $('reviewRequestModal');
      if (m && m.classList.contains('is-open')) {
        e.preventDefault();
        closeModal();
      }
    });
  }

  function rebindSubmit(handler) {
    const btn = $('reviewRequestSubmitBtn');
    if (!btn) return;
    // 기존 리스너 제거(간단/확실): 버튼을 clone 해서 교체
    const clone = btn.cloneNode(true);
    btn.replaceWith(clone);
    clone.addEventListener('click', handler);
  }

  function readPayload() {
    const orderIdStr = normalizeStr($('reviewRequestOrderId')?.value);
    const orderId = Number(orderIdStr);
    const githubPrUrl = normalizeStr($('reviewRequestGithubPrUrl')?.value);
    const projectContext = normalizeStr($('reviewRequestProjectContext')?.value);
    const concernPoint = normalizeStr($('reviewRequestConcernPoint')?.value);
    return { orderIdStr, orderId, githubPrUrl, projectContext, concernPoint };
  }

  async function submitWith(opts) {
    const method = (opts && opts.method) ? String(opts.method).toUpperCase() : 'POST';
    const url = opts && opts.url ? String(opts.url) : '/reviews/request';

    const { orderIdStr, orderId, githubPrUrl, projectContext, concernPoint } = readPayload();

    if (!orderIdStr || !Number.isFinite(orderId) || orderId <= 0) {
      return setError('주문 정보를 찾지 못했어요. 다시 시도해 주세요.');
    }
    if (!githubPrUrl) return setError('GitHub PR 링크를 입력해 주세요.');
    if (!isValidGithubPrUrl(githubPrUrl)) return setError('GitHub PR 링크 형식이 올바르지 않아요.');
    if (!projectContext) return setError('배경/비즈니스 로직을 입력해 주세요.');
    if (!concernPoint) return setError('질문/고민 포인트를 입력해 주세요.');
    if (projectContext.length < 10) return setError('배경/비즈니스 로직은 10자 이상 입력해 주세요.');
    if (concernPoint.length < 10) return setError('질문/고민 포인트는 10자 이상 입력해 주세요.');

    setError('');
    setLoading(true, opts?.submitText);

    try {
      const res = await fetch(url, {
        method,
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ orderId, githubPrUrl, projectContext, concernPoint })
      });
      if (!res.ok) throw new Error('request failed');
      const data = await res.json().catch(function () { return {}; });
      const nextOrderId = (data && data.orderId) ? String(data.orderId) : orderIdStr;
      window.location.href = '/orders/' + encodeURIComponent(nextOrderId);
    } catch (e) {
      setError(opts?.failMessage || '제출에 실패했어요. 잠시 후 다시 시도해 주세요.');
      setLoading(false, opts?.submitText);
    }
  }

  window.ReviewRequestModal = {
    close: closeModal,
    open: function (opts) {
      bindCloseOnce();
      setError('');

      const mode = opts && opts.mode ? String(opts.mode) : 'create';
      const title = mode === 'edit' ? '리뷰 요청서 수정' : '리뷰 요청서 작성';
      const submitText = mode === 'edit' ? '수정해서 다시 제출' : '제출하고 워크스페이스 입장';

      const titleEl = $('reviewRequestModalTitle');
      if (titleEl) titleEl.textContent = title;

      const orderId = opts && opts.orderId != null ? String(opts.orderId) : '';
      const orderEl = $('reviewRequestOrderId');
      if (orderEl) orderEl.value = orderId;

      const prEl = $('reviewRequestGithubPrUrl');
      const ctxEl = $('reviewRequestProjectContext');
      const conEl = $('reviewRequestConcernPoint');
      if (prEl) prEl.value = opts?.githubPrUrl || '';
      if (ctxEl) ctxEl.value = opts?.projectContext || '';
      if (conEl) conEl.value = opts?.concernPoint || '';

      // submit 버튼 텍스트 초기화
      const btn = $('reviewRequestSubmitBtn');
      if (btn) {
        btn.dataset.originalText = submitText;
        btn.textContent = submitText;
      }

      // submit handler를 현재 mode에 맞게 재바인딩
      rebindSubmit(function () {
        submitWith({
          method: opts?.method || (mode === 'edit' ? 'PATCH' : 'POST'),
          url: opts?.url || '/reviews/request',
          submitText,
          failMessage: opts?.failMessage || (mode === 'edit'
            ? '수정에 실패했어요. 리포트가 작성되었거나 권한이 없을 수 있어요.'
            : '제출에 실패했어요. 잠시 후 다시 시도해 주세요.')
        });
      });

      openModal();
      setTimeout(function () { if (prEl) prEl.focus(); }, 50);
    }
  };
})();

