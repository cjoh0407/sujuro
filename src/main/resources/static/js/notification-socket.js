/*
 * 실시간 알림 (FR-034)
 * 로그인한 모든 화면에서 서버와 웹소켓으로 연결해 두고, 새 알림이 오면
 *   1) 헤더 종 아이콘의 안 읽은 건수를 바꾸고
 *   2) 화면 아래에 알림 내용을 잠깐 보여 준다.
 * 연결이 끊기면 조금씩 간격을 늘려 가며 다시 연결한다. (알림 자체는 DB에 저장돼 있어 목록에서 항상 볼 수 있다.)
 */
(function () {
  'use strict';
  if (!('WebSocket' in window)) { return; }

  // 이 파일의 주소에서 앱의 시작 경로(contextPath)를 알아낸다.
  var script = document.currentScript;
  var base = '';
  if (script && script.src) {
    var path = new URL(script.src, location.href).pathname;
    base = path.replace(/\/js\/notification-socket\.js.*$/, '');
  }
  var url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + base + '/ws/notifications';

  var retry = 0;
  var closedByPage = false;
  window.addEventListener('beforeunload', function () { closedByPage = true; });

  function updateBadge(count) {
    var bell = document.querySelector('.bellbtn');
    if (!bell) { return; }
    var badge = bell.querySelector('.badge');
    if (!count || count < 1) {
      if (badge) { badge.remove(); }
      return;
    }
    if (!badge) {
      badge = document.createElement('span');
      badge.className = 'badge';
      bell.appendChild(badge);
    }
    badge.textContent = count > 99 ? '99+' : String(count);
  }

  function connect() {
    var ws;
    try { ws = new WebSocket(url); } catch (e) { return; }

    ws.onopen = function () { retry = 0; };

    ws.onmessage = function (ev) {
      var data;
      try { data = JSON.parse(ev.data); } catch (e) { return; }
      if (typeof data.unreadCount === 'number') { updateBadge(data.unreadCount); }
      if (data.content && typeof window.showToast === 'function') { window.showToast('🔔 ' + data.content); }
      // 알림 목록 화면이 열려 있으면 새 알림이 보이도록 알려 준다.
      document.dispatchEvent(new CustomEvent('sujuro:notification', { detail: data }));
    };

    ws.onclose = function () {
      if (closedByPage) { return; }
      retry = Math.min(retry + 1, 6);
      setTimeout(connect, Math.min(30000, 1000 * Math.pow(2, retry)));
    };
  }

  connect();
})();
