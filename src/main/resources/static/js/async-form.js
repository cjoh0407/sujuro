/*
 * 비동기 폼 전송 (화면 새로고침 없이 저장)
 *
 * 쓰는 법
 *   화면의 head 조각에 <meta name="async-forms" content="on"> 한 줄을 넣는다.
 *   그 화면 본문(<main>) 안의 폼은 모두 새로고침 없이 보내진다. 빼고 싶은 폼만 data-async="off".
 *   select 를 고르자마자 보낼 때는 form.submit() 대신 form.requestSubmit() 을 쓴다.
 *   (submit()은 submit 이벤트가 생기지 않아 여기서 가로챌 수 없다)
 *
 * 동작 (서버 코드는 바꾸지 않는다)
 *   1) submit 이벤트를 가로채 fetch 로 보낸다. CSRF 토큰은 th:action 폼의 hidden 칸에 이미 들어 있다.
 *   2) 서버는 지금처럼 처리하고 redirect 한다. fetch 가 redirect 를 따라가 결과 화면 HTML 을 받는다.
 *      (완료·오류 문구는 flash 로 그 화면에 들어 있다)
 *   3) 받은 HTML 에서 <main> 내용만 꺼내 지금 <main> 과 바꾼다. 페이지 스크롤과 표 안 스크롤은 그대로 둔다.
 *   4) 완료·오류 안내 띠(data-flash)는 화면 맨 위에 보이지 않게 뺀다. 오류만 빨간 토스트로 보여 주고 완료 문구는 띄우지 않는다.
 *   5) onPageReady 로 등록한 함수를 다시 불러 새 내용에 이벤트를 붙인다.
 *   결과가 다른 주소(로그인 만료, 다른 화면으로 이동)면 그 화면으로 그냥 이동한다.
 *   스크립트가 꺼져 있거나 오래된 브라우저면 원래대로 새로고침 방식으로 동작한다.
 *
 * 탭 전환 · 화면 안 이동
 *   같은 화면 안의 탭(a.tab), 또는 data-nav 를 붙인 링크(같은 주소에서 ?view=… 만 바뀌는 이동)를 누르면 본문만 갈아 끼우고 주소(?tab=...)를 바꾼다. 빼려면 링크에 data-async="off".
 */
(function () {
  var meta = document.querySelector('meta[name="async-forms"]');
  if (!meta || meta.getAttribute('content') !== 'on' || !window.fetch || !window.DOMParser) return;
  var main = document.querySelector('main.content');
  if (!main) return;
  var pushed = false; // 검색(GET)으로 주소를 바꾼 적이 있는지

  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (e.defaultPrevented || !(form instanceof HTMLFormElement)) return; // onsubmit 확인창에서 취소한 경우
    if (!main.contains(form) || form.getAttribute('data-async') === 'off') return;
    if (form.target && form.target !== '_self') return;
    e.preventDefault();
    send(form, e.submitter);
  });

  /* 탭 전환(a.tab): 같은 화면 안에서 탭만 바뀔 때는 본문만 갈아 끼운다. 새 탭·Ctrl/Shift 클릭, 다른 화면으로 가는 링크는 그대로 이동한다.
     가져오지 못하거나 응답이 이상하면 원래대로 그 주소로 이동한다 */
  document.addEventListener('click', function (e) {
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    var a = e.target.closest && e.target.closest('a.tab, a[data-nav]');
    if (!a || !main.contains(a) || a.getAttribute('data-async') === 'off') return;
    if (a.target && a.target !== '_self') return;
    var to = new URL(a.href, location.href);
    if (to.origin !== location.origin || to.pathname !== location.pathname || to.href === location.href) return;
    e.preventDefault();
    if (main.classList.contains('async-busy')) return;
    main.classList.add('async-busy');
    fetch(to.href, { credentials: 'same-origin', headers: { 'X-Requested-With': 'fetch' } })
      .then(function (res) {
        return res.text().then(function (html) { return { res: res, html: html }; });
      })
      .then(function (r) {
        var next = r.res.ok && new URL(r.res.url).pathname === location.pathname
          ? new DOMParser().parseFromString(r.html, 'text/html').querySelector('main.content') : null;
        if (!next) { location.href = to.href; return; }
        swap(next);
        history.pushState({ async: true }, '', r.res.url);
        pushed = true;
      })
      .catch(function () { location.href = to.href; })
      .then(function () { main.classList.remove('async-busy'); });
  });

  // 검색(GET)으로 바꾼 주소에서 [뒤로 가기] 하면 그 주소 화면을 새로 연다
  window.addEventListener('popstate', function () { if (pushed) location.reload(); });

  function send(form, submitter) {
    if (form.getAttribute('aria-busy') === 'true') return; // 응답 오기 전 두 번 누름 방지
    var method = ((submitter && submitter.getAttribute('formmethod')) || form.getAttribute('method') || 'get').toLowerCase();
    var action = submitter && submitter.hasAttribute('formaction') ? submitter.formAction : form.action;
    var data = new FormData(form);
    if (submitter && submitter.name) data.append(submitter.name, submitter.value);

    var url = action;
    var opts = { method: method === 'post' ? 'POST' : 'GET', credentials: 'same-origin', headers: { 'X-Requested-With': 'fetch' } };
    if (method === 'post') {
      opts.body = data;
    } else {
      var u = new URL(action, location.href), q = new URLSearchParams();
      data.forEach(function (v, k) { if (typeof v === 'string') q.append(k, v); });
      u.search = q.toString();
      url = u.toString();
    }

    busy(form, true);
    fetch(url, opts)
      .then(function (res) {
        return res.text().then(function (html) { return { res: res, html: html }; });
      })
      .then(function (r) {
        var res = r.res;
        if (!res.ok) { showToast(errorText(res.status), 'err'); return; }
        var to = new URL(res.url);
        if (to.pathname !== location.pathname) { location.href = res.url; return; }
        var next = new DOMParser().parseFromString(r.html, 'text/html').querySelector('main.content');
        if (!next) { location.href = res.url; return; }
        swap(next);
        if (opts.method === 'GET') { history.pushState({ async: true }, '', res.url); pushed = true; }
        else history.replaceState(history.state, '', res.url);
      })
      .catch(function () {
        showToast('서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.', 'err');
      })
      .then(function () { busy(form, false); });
  }

  /* <main> 내용을 바꾼다. 스크롤·포커스는 그대로, 안내 띠는 토스트로 */
  function swap(next) {
    var flashes = [];
    Array.prototype.forEach.call(next.querySelectorAll('[data-flash]'), function (b) {
      flashes.push({ text: b.textContent.trim(), err: b.getAttribute('data-flash') === 'error' });
      b.remove();
    });
    var scrolls = keepScroll();
    var opened = Array.prototype.map.call(main.querySelectorAll('details'), function (d) { return d.open; }); // 펼쳐 둔 "지난 사업" 등
    var focusId = document.activeElement && document.activeElement.id;
    var y = window.scrollY;
    var gauges = readGauges();

    main.innerHTML = next.innerHTML;
    animateGauges(gauges);

    restoreScroll(scrolls);
    Array.prototype.forEach.call(main.querySelectorAll('details'), function (d, i) { if (opened[i]) d.open = true; });
    window.scrollTo(window.scrollX, y);
    if (focusId) {
      var f = document.getElementById(focusId);
      if (f && f.focus) f.focus({ preventScroll: true });
    }
    if (window.runPageReady) window.runPageReady();
    // 저장·삭제 완료 문구는 화면이 바뀐 것으로 충분해서 띄우지 않고, 막힌 경우(오류)만 빨간 토스트로 알린다 (11/07)
    flashes.forEach(function (m) { if (m.err) showToast(m.text, 'err'); });
  }

  /* 진행 게이지: 바뀌기 전 너비를 기억했다가 새 화면에서 그 너비부터 새 값까지 차오르게 한다 (처음 열 때는 0부터) */
  function readGauges() {
    var m = {};
    Array.prototype.forEach.call(main.querySelectorAll('.gauge[data-gauge] > i'), function (i) {
      m[i.parentNode.getAttribute('data-gauge')] = i.style.width;
    });
    return m;
  }

  function animateGauges(prev) {
    Array.prototype.forEach.call(main.querySelectorAll('.gauge[data-gauge] > i'), function (i) {
      var target = i.style.width;
      var from = prev[i.parentNode.getAttribute('data-gauge')] || '0%';
      if (from === target) return;
      i.style.transition = 'none';
      i.style.width = from;
      void i.offsetWidth;
      i.style.transition = '';
      i.style.width = target;
    });
  }
  document.addEventListener('DOMContentLoaded', function () { if (main) animateGauges({}); });

  /* 표·목록 안에서 스크롤해 둔 위치를 id 기준으로 기억했다가 되돌린다 */
  function keepScroll() {
    var list = [];
    Array.prototype.forEach.call(main.querySelectorAll('[id]'), function (el) {
      if (el.scrollTop || el.scrollLeft) list.push({ id: el.id, top: el.scrollTop, left: el.scrollLeft });
    });
    Array.prototype.forEach.call(main.querySelectorAll('.ts'), function (el, i) {
      if (el.scrollLeft) list.push({ ts: i, left: el.scrollLeft });
    });
    return list;
  }

  function restoreScroll(list) {
    var ts = main.querySelectorAll('.ts');
    list.forEach(function (s) {
      var el = s.id ? document.getElementById(s.id) : ts[s.ts];
      if (!el) return;
      if (s.top) el.scrollTop = s.top;
      if (s.left) el.scrollLeft = s.left;
    });
  }

  function busy(form, on) {
    if (on) form.setAttribute('aria-busy', 'true');
    else form.removeAttribute('aria-busy');
    main.classList.toggle('async-busy', on);
  }

  function errorText(status) {
    if (status === 403) return '이 작업을 할 권한이 없습니다.';
    if (status === 404) return '대상을 찾을 수 없습니다. 화면을 새로고침해 주세요.';
    if (status === 413) return '파일이 너무 커서 올리지 못했습니다.';
    return '처리하지 못했습니다. (오류 ' + status + ') 잠시 후 다시 시도해 주세요.';
  }
})();
