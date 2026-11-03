/*
 * 알림 팝업 (FR-032, FR-033). 헤더 종 아이콘을 누르면 열린다. 따로 알림 화면은 없다.
 *   - 처음 열 때 최근 20건을 불러오고, 목록 스크롤이 끝에 닿으면 20건씩 더 불러온다.
 *   - 안 읽은 알림은 진하게(왼쪽 점), 읽은 알림은 흐리게 보인다.
 *   - 알림을 누르면 읽음 처리하고 그 알림의 화면(공고 상세, 제안, 일정의 그 달, 제출서류, 평가·결과)으로 간다.
 *     갈 화면이 없으면(제안팀에서 빠지는 등 권한이 없어진 경우) 읽음 처리만 하고 안내한다.
 *   - [모두 읽음], 바깥 클릭·Esc 로 닫기. 새 알림이 오면(notification-socket.js) 다시 불러온다.
 * 대시보드 '최근 알림'도 같은 열기 함수(window.sujuroOpenAlert)를 쓴다.
 */
(function () {
  'use strict';
  var btn = document.getElementById('bellBtn');
  var panel = document.getElementById('bellPanel');
  if (!btn || !panel) { return; }

  var base = btn.getAttribute('data-base');            // 앱 시작 경로 + /alerts
  var list = document.getElementById('bpList');
  var foot = document.getElementById('bpFoot');
  var unreadEl = document.getElementById('bpUnread');
  var readAllBtn = document.getElementById('bpReadAll');
  var PAGE = 20;
  var offset = 0, more = true, loading = false, loaded = false, seen = {};

  function headers() {
    var h = { 'Accept': 'application/json' };
    if (window.csrf && window.csrf.header && window.csrf.token) { h[window.csrf.header] = window.csrf.token; }
    return h;
  }

  function setBadge(count) {
    var badge = btn.querySelector('.badge');
    if (!count || count < 1) { if (badge) { badge.remove(); } return; }
    if (!badge) { badge = document.createElement('span'); badge.className = 'badge'; btn.appendChild(badge); }
    badge.textContent = count > 99 ? '99+' : String(count);
  }

  function setUnread(count) {
    unreadEl.textContent = count > 0 ? '안 읽음 ' + count + '건' : '';
    readAllBtn.disabled = !(count > 0);
    setBadge(count);
  }

  function render(it) {
    var li = document.createElement('li');
    li.className = 'noti-item' + (it.read ? ' read' : '');
    var b = document.createElement('button');
    b.type = 'button';
    b.title = '눌러서 열기';
    var c = document.createElement('span');
    c.className = 'c';
    c.textContent = it.content || '';
    var m = document.createElement('span');
    m.className = 'm';
    var pill = document.createElement('span');
    pill.className = 'pill neutral';
    pill.textContent = it.typeNm || '';
    var t = document.createElement('span');
    t.textContent = it.time || '';
    m.appendChild(pill);
    m.appendChild(t);
    b.appendChild(c);
    b.appendChild(m);
    b.addEventListener('click', function () { window.sujuroOpenAlert(it.notiSn, li); });
    li.appendChild(b);
    return li;
  }

  function showFoot() {
    if (!list.children.length) { foot.textContent = '받은 알림이 없습니다.'; }
    else { foot.textContent = more ? '' : '더 이전 알림이 없습니다.'; }
  }

  function load(reset) {
    if (loading) { return; }
    if (reset) { offset = 0; more = true; seen = {}; list.innerHTML = ''; }
    if (!more) { return; }
    loading = true;
    foot.textContent = '불러오는 중…';
    fetch(base + '/list?offset=' + offset + '&size=' + PAGE, { headers: headers(), credentials: 'same-origin' })
      .then(function (r) { if (!r.ok) { throw new Error(r.status); } return r.json(); })
      .then(function (d) {
        (d.items || []).forEach(function (it) {
          if (seen[it.notiSn]) { return; }
          seen[it.notiSn] = true;
          list.appendChild(render(it));
        });
        offset += (d.items || []).length;
        more = !!d.hasMore;
        loaded = true;
        if (typeof d.unreadCount === 'number') { setUnread(d.unreadCount); }
        loading = false;
        showFoot();
        // 목록이 짧아 스크롤이 생기지 않으면 바로 다음 쪽을 불러온다
        if (more && list.scrollHeight <= list.clientHeight + 4) { load(false); }
      })
      .catch(function () {
        loading = false;
        loaded = false;
        foot.textContent = '알림을 불러오지 못했습니다. 다시 열어 주세요.';
      });
  }

  function open() {
    panel.classList.add('on');
    btn.setAttribute('aria-expanded', 'true');
    if (!loaded) { load(true); }
  }
  function close() {
    panel.classList.remove('on');
    btn.setAttribute('aria-expanded', 'false');
  }
  window.sujuroBell = function (show) { if (show === false) { close(); } else { open(); } };

  /** 알림 하나 열기: 읽음 처리 → 갈 화면이 있으면 이동, 없으면 안내 */
  window.sujuroOpenAlert = function (notiSn, el) {
    fetch(base + '/' + encodeURIComponent(notiSn) + '/read', { method: 'POST', headers: headers(), credentials: 'same-origin' })
      .then(function (r) { if (!r.ok) { throw new Error(r.status); } return r.json(); })
      .then(function (d) {
        if (el) { el.classList.add('read'); el.classList.remove('unread'); }
        if (typeof d.unreadCount === 'number') { setUnread(d.unreadCount); }
        if (d.link) { location.href = d.link; return; }
        if (typeof window.showToast === 'function') {
          window.showToast('이 알림의 화면을 볼 수 있는 권한이 없어졌거나, 이동할 화면이 없습니다.');
        }
      })
      .catch(function () {
        if (typeof window.showToast === 'function') { window.showToast('알림을 열지 못했습니다. 잠시 뒤 다시 해 주세요.'); }
      });
  };

  btn.addEventListener('click', function () { if (panel.classList.contains('on')) { close(); } else { open(); } });

  readAllBtn.addEventListener('click', function () {
    fetch(base + '/read-all', { method: 'POST', headers: headers(), credentials: 'same-origin' })
      .then(function (r) { if (!r.ok) { throw new Error(r.status); } return r.json(); })
      .then(function () {
        Array.prototype.forEach.call(list.querySelectorAll('.noti-item'), function (li) { li.classList.add('read'); });
        Array.prototype.forEach.call(document.querySelectorAll('.dash-alert.unread'), function (f) { f.classList.remove('unread'); });
        setUnread(0);
      })
      .catch(function () {
        if (typeof window.showToast === 'function') { window.showToast('모두 읽음 처리하지 못했습니다.'); }
      });
  });

  list.addEventListener('scroll', function () {
    if (list.scrollTop + list.clientHeight >= list.scrollHeight - 40) { load(false); }
  });

  // 바깥을 누르면 닫는다. 팝업을 여는 다른 버튼([data-bell-open], 대시보드 '전체 보기')은 제외
  document.addEventListener('click', function (e) {
    if (!panel.classList.contains('on')) { return; }
    if (panel.contains(e.target) || btn.contains(e.target)) { return; }
    if (e.target.closest && e.target.closest('[data-bell-open]')) { return; }
    close();
  });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { close(); } });

  // 대시보드 '최근 알림': 폼 대신 같은 방식으로 연다 (스크립트가 없으면 폼이 /alerts/{id}/open 으로 간다)
  document.addEventListener('submit', function (e) {
    var f = e.target;
    if (!f.getAttribute || !f.getAttribute('data-noti-id')) { return; }
    e.preventDefault();
    window.sujuroOpenAlert(f.getAttribute('data-noti-id'), f);
  });

  // 새 알림이 오면: 열려 있으면 바로 다시 불러오고, 닫혀 있으면 다음에 열 때 불러온다
  document.addEventListener('sujuro:notification', function () {
    if (panel.classList.contains('on')) { load(true); } else { loaded = false; }
  });
})();
