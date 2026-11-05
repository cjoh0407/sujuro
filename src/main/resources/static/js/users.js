/* 사용자 관리(user/users.html): 등록·수정 모달, 직급 칸, 닫기
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 같은 자리에서 <script th:src> 로 불러온다. */
(function () {
  var modal = document.getElementById('usrFormModal');
  var form = document.getElementById('usrForm');
  var team = document.getElementById('teamSn');
  var box = document.getElementById('jbgdBox');
  var rank = document.getElementById('jbgdCd');
  if (!modal || !form) { return; }

  // 경영지원실(DP010)·영업부(DP020)를 고르면 직급 칸을 숨기고 비운다. 서버도 같은 기준으로 직급을 비운다.
  function syncRank() {
    var opt = team.options[team.selectedIndex];
    var dept = opt ? opt.getAttribute('data-dept') : null;
    var none = dept === 'DP010' || dept === 'DP020';
    box.style.display = none ? 'none' : '';
    rank.disabled = none;
    rank.required = !none;
    if (none) { rank.value = ''; }
  }
  team.addEventListener('change', syncRank);

  function open(m) { m.hidden = false; var f = m.querySelector('input:not([type=hidden]):not([readonly]), select'); if (f) { f.focus(); } }
  window.usrClose = function (el) { el.closest('.modal').hidden = true; };

  // 등록·수정을 한 모달로 쓴다. 서버에 다시 묻지 않고 목록 줄의 값으로 채운다.
  function setMode(edit, d) {
    var errs = document.getElementById('usrErrors');
    if (errs) { errs.hidden = true; }
    var uid = document.getElementById('userId');
    document.getElementById('usrFormTitle').textContent = edit ? '직원 정보 수정 · ' + d.nm : '직원 등록';
    document.getElementById('usrSubmit').textContent = edit ? '수정 저장' : '등록 · 초기 비밀번호 메일 발송';
    document.getElementById('usrHelpCreate').hidden = edit;
    document.getElementById('usrHelpEdit').hidden = !edit;
    form.action = edit ? modal.dataset.base + '/' + encodeURIComponent(d.id) + '/update' : modal.dataset.base;
    uid.readOnly = edit;
    uid.required = !edit;
    uid.value = d.id || '';
    document.getElementById('userNm').value = d.nm || '';
    team.value = d.team || '';
    rank.value = d.jbgd || '';
    document.getElementById('telno').value = d.tel || '';
    document.getElementById('emlAddr').value = d.eml || '';
    syncRank();
    open(modal);
  }
  window.usrCreate = function () { setMode(false, {}); };
  window.usrEdit = function (btn) { setMode(true, btn.dataset); };

  // 닫기: 바깥 배경 클릭, Esc
  Array.prototype.forEach.call(document.querySelectorAll('.usr .modal'), function (m) {
    m.addEventListener('click', function (e) { if (e.target === m) { m.hidden = true; } });
  });
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') { return; }
    Array.prototype.forEach.call(document.querySelectorAll('.usr .modal'), function (m) { m.hidden = true; });
  });

  // 서버가 다시 그린 화면: 입력 오류면 등록·수정 모달을, 팀 이동·비활성 확인이면 확인 모달을 연다
  syncRank();
  if (modal.dataset.open) { open(modal); }
  var confirmModal = document.getElementById('usrConfirmModal');
  if (confirmModal) { confirmModal.hidden = false; }
})();
