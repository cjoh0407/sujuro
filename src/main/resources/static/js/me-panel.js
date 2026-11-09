/*
 * 내 정보 팝업 (11/09). 헤더의 사람 아이콘을 누르면 열린다. 따로 내 정보 화면은 없다.
 *   - 이름·권한·소속과 사번·소속·직급·권한을 보여 주고, [비밀번호 변경]은 /password/change 로 간다.
 *   - 바깥 클릭·Esc 로 닫는다. 알림 종 팝업(notification-panel.js)도 바깥 클릭으로 닫히므로 둘이 동시에 열려 있지 않는다.
 */
(function () {
  'use strict';
  var btn = document.getElementById('meBtn');
  var panel = document.getElementById('mePanel');
  if (!btn || !panel) { return; }

  function open() { panel.classList.add('on'); btn.setAttribute('aria-expanded', 'true'); }
  function close() { panel.classList.remove('on'); btn.setAttribute('aria-expanded', 'false'); }

  btn.addEventListener('click', function () {
    if (panel.classList.contains('on')) { close(); } else { open(); }
  });
  document.addEventListener('click', function (e) {
    if (!panel.classList.contains('on')) { return; }
    if (panel.contains(e.target) || btn.contains(e.target)) { return; }
    close();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && panel.classList.contains('on')) { close(); btn.focus(); }
  });
})();
