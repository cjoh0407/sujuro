/* 일정 관리 화면 (schedule/index.html): 등록 모달, 수정·삭제 모달 */
function schOpen(id) {
  var m = document.getElementById(id);
  if (!m) return;
  m.hidden = false;
  var first = m.querySelector('input[type=text]:not([readonly]), select');
  if (first) first.focus();
}

function schClose(id) {
  var m = document.getElementById(id);
  if (m) m.hidden = true;
}

/* 캘린더에서 우리 팀 일정을 누르면 값을 채워 수정·삭제 모달을 연다.
   지난 일정이나 끝난 사업의 일정은 내용을 고칠 수 없고 삭제만 된다 (서버에서도 막는다) */
function schOpenEdit(btn) {
  var d = btn.dataset;
  var changeable = d.changeable === 'true';
  document.getElementById('se-id').value = d.id;
  document.getElementById('se-del-id').value = d.id;
  document.getElementById('se-pbanc').textContent = d.pbanc || '';
  document.getElementById('se-title').value = d.title || '';
  document.getElementById('se-date').value = d.date || '';
  document.getElementById('se-time').value = d.time || '';
  ['se-title', 'se-date', 'se-time'].forEach(function (id) {
    document.getElementById(id).disabled = !changeable;
  });
  document.getElementById('se-save').hidden = !changeable;
  var lock = document.getElementById('se-lock');
  lock.hidden = changeable;
  lock.textContent = d.reason || '';
  document.getElementById('schEditTitle').textContent = changeable ? '일정 수정' : '일정';
  schOpen('schEdit');
}

function schDelete() {
  var title = document.getElementById('se-title').value;
  if (window.confirm('\'' + title + '\' 일정을 삭제하시겠습니까?\n삭제하면 되돌릴 수 없고, 제안팀원에게 삭제 알림이 갑니다.')) {
    document.getElementById('se-del').submit();
  }
}

/* Esc 로 열린 모달을 닫는다 */
document.addEventListener('keydown', function (e) {
  if (e.key !== 'Escape') return;
  ['schAdd', 'schEdit'].forEach(schClose);
});
