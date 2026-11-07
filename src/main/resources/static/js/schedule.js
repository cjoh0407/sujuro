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
  document.getElementById('schEditTitle').textContent = changeable ? '일정 수정' : '일정';
  schOpen('schEdit');
}

/* 수정 권한이 없는 사람이 일정을 누르면 내용만 보여 준다 (자동 마감 일정 포함) */
function schOpenView(btn) {
  var d = btn.dataset;
  document.getElementById('sv-title').value = d.title || '';
  document.getElementById('sv-date').value = d.date || '';
  document.getElementById('sv-time').value = d.time || '';
  document.getElementById('sv-pbanc').textContent = d.pbanc || '';
  schOpen('schView');
}

function schDelete() {
  var title = document.getElementById('se-title').value;
  if (window.confirm('「' + title + '」 일정을 삭제할까요?\n삭제하면 되돌릴 수 없습니다. 제안팀원에게 삭제 알림이 갑니다.')) {
    document.getElementById('se-del').requestSubmit(); // 비동기 저장(async-form.js)이 가로챌 수 있게
  }
}

/* Esc 로 열린 모달과 연도·월 고르기 창을 닫는다 */
document.addEventListener('keydown', function (e) {
  if (e.key !== 'Escape') return;
  ['schAdd', 'schEdit', 'schView'].forEach(schClose);
  schYmClose();
});

/* ===== 연도·월 고르기 (11/07) =====
   제목 버튼의 data-ym(보는 달), data-now(이번 달), data-base(고른 팀·사업이 들어 있는 주소)로 1~12월 링크를 만든다.
   화면이 비동기로 바뀌어도 동작하도록 요소는 부를 때마다 찾는다. */
function schYmEls() {
  return { btn: document.querySelector('.sch-ym-btn'), pop: document.querySelector('.sch-ym-pop') };
}

function schYmRender() {
  var el = schYmEls();
  var y = parseInt(el.pop.getAttribute('data-year'), 10);
  var cur = el.btn.getAttribute('data-ym');
  var now = el.btn.getAttribute('data-now');
  el.pop.querySelector('.sch-ym-y').textContent = y + '년';
  var box = el.pop.querySelector('.sch-ym-mons');
  box.textContent = '';
  for (var m = 1; m <= 12; m++) {
    var ym = y + '-' + (m < 10 ? '0' + m : m);
    var url = new URL(el.btn.getAttribute('data-base'), location.href);
    url.searchParams.set('ym', ym);
    var a = document.createElement('a');
    a.href = url.pathname + url.search;
    a.textContent = m + '월';
    if (ym === cur) { a.className = 'on'; a.setAttribute('aria-current', 'true'); }
    else if (ym === now) { a.className = 'now'; }
    box.appendChild(a);
  }
}

function schYmToggle() {
  var el = schYmEls();
  if (!el.btn || !el.pop) return;
  if (!el.pop.hidden) { schYmClose(); return; }
  el.pop.setAttribute('data-year', el.btn.getAttribute('data-ym').slice(0, 4));
  schYmRender();
  el.pop.hidden = false;
  el.btn.setAttribute('aria-expanded', 'true');
  var focus = el.pop.querySelector('.sch-ym-mons a.on') || el.pop.querySelector('.sch-ym-mons a');
  if (focus) focus.focus();
}

function schYmYear(step) {
  var el = schYmEls();
  if (!el.pop) return;
  el.pop.setAttribute('data-year', parseInt(el.pop.getAttribute('data-year'), 10) + parseInt(step, 10));
  schYmRender();
}

function schYmClose() {
  var el = schYmEls();
  if (!el.pop || el.pop.hidden) return;
  el.pop.hidden = true;
  el.btn.setAttribute('aria-expanded', 'false');
}

/* 창 바깥을 누르면 닫는다 */
document.addEventListener('click', function (e) {
  if (e.target.closest && e.target.closest('.sch-ym')) return;
  schYmClose();
});

/* 드래그로 일정 옮기기 (주관팀 팀장, 우리 팀이 등록한 일정).
   다른 날짜 칸에 놓으면 날짜만 바꿔 저장한다(시간은 그대로). 오늘 이전 칸에는 놓을 수 없다.
   권한·지난 일정·끝난 사업은 서버에서 다시 막고, 막히면 이유가 토스트로 나온다.
   비동기 저장으로 달력이 바뀌면 새 칸에 다시 붙여야 하므로 onPageReady 로 등록한다. */
onPageReady(function () {
  var form = document.getElementById('sch-move');
  if (!form) return;
  var today = form.getAttribute('data-today');
  var dragging = null;

  function clear(cell) { cell.classList.remove('drop-ok', 'drop-no'); }
  function canDrop(cell) { return cell.dataset.date && cell.dataset.date >= today; }

  Array.prototype.forEach.call(document.querySelectorAll('.sch .sch-ev[draggable="true"]'), function (ev) {
    ev.addEventListener('dragstart', function (e) {
      dragging = ev;
      ev.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', ev.dataset.id);
    });
    ev.addEventListener('dragend', function () {
      ev.classList.remove('dragging');
      dragging = null;
      Array.prototype.forEach.call(document.querySelectorAll('.sch .cal .cell'), clear);
    });
  });

  Array.prototype.forEach.call(document.querySelectorAll('.sch .cal .cell[data-date]'), function (cell) {
    cell.addEventListener('dragover', function (e) {
      if (!dragging) return;
      if (!canDrop(cell)) {
        cell.classList.add('drop-no');
        e.dataTransfer.dropEffect = 'none';
        return;
      }
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      cell.classList.add('drop-ok');
    });
    cell.addEventListener('dragleave', function (e) {
      if (e.relatedTarget && cell.contains(e.relatedTarget)) return;
      clear(cell);
    });
    cell.addEventListener('drop', function (e) {
      e.preventDefault();
      clear(cell);
      if (!dragging || !canDrop(cell)) return;
      if (cell.dataset.date === dragging.dataset.date) return; // 같은 날짜면 그대로
      document.getElementById('sm-id').value = dragging.dataset.id;
      document.getElementById('sm-date').value = cell.dataset.date;
      form.requestSubmit();
    });
  });
});
