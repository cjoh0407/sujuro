/* 제안 업무 화면 공통 스크립트 (templates/proposal: 목차·요구사항, 제안서 파일, 제출서류) */

/* "하위 목차 추가" / "같은 단계 목차 추가" 입력 줄을 연다. 버튼의 data-t(목차), data-m(d: 하위, s: 같은 단계), data-label, data-depth */
function openAdd(btn) {
  document.querySelectorAll('tr.addrow').forEach(function (r) { r.hidden = true; });
  var d = btn.dataset, row = document.getElementById('add-' + d.t);
  if (!row) return;
  var f = row.querySelector('form'), child = d.m === 'd';
  f.elements.mode.value = d.m;
  row.querySelector('.cap').textContent = child ? '"' + d.label + '"의 하위 목차' : '"' + d.label + '"과 같은 단계';
  row.querySelector('td').style.paddingLeft = (14 + (+d.depth + (child ? 1 : 0)) * 20) + 'px';
  row.hidden = false;
  f.elements.label.focus();
}

function closeAdd(btn) {
  var row = btn.closest('tr');
  row.hidden = true;
  row.querySelectorAll('[data-track]').forEach(function (i) { i.value = i.defaultValue; });
}

/* 최하위 목차에 하위를 만들면 담당자·쪽수가 비워지고 연결된 요구사항은 미매핑이 된다 */
function confirmAdd(f) {
  var d = f.dataset;
  if (f.elements.mode.value !== 'd' || d.leaf !== 'true' || (+d.rqts === 0 && d.pic !== 'true')) return true;
  return confirm('"' + d.label + '"에 하위 목차를 만들면 이 목차는 최하위가 아니게 되어 담당자·쪽수가 비워집니다.'
    + (+d.rqts > 0 ? ' 연결된 요구사항 ' + d.rqts + '건은 미매핑이 됩니다.' : '') + ' 계속할까요?');
}

/* 목차 삭제 확인 (묶음은 하위 포함) */
function confirmDel(f) {
  var d = f.dataset, msg = '"' + d.label + '"' + (d.group === 'true' ? '과 하위 목차' : '') + '를 삭제합니다.';
  if (+d.rqts > 0) msg += ' 연결된 요구사항 ' + d.rqts + '건은 미매핑이 됩니다.';
  return confirm(msg + ' 삭제할까요?');
}

/* 파일 삭제 사유를 한 줄 받는다. 비우면 취소. 폼에 name="reason" hidden 칸이 있어야 한다. (NFR-009: 숨김 삭제) */
function askReason(f) {
  var r = prompt('삭제 사유를 입력해 주세요. (목록에서만 숨기고 원본과 기록은 남습니다)');
  if (r === null || !r.trim()) return false;
  f.elements.reason.value = r.trim();
  return true;
}

/* ---------- 목차 양식 (FR-048) ---------- */

/* 이름 바꾸기: 새 이름을 받아 hidden tocFormNm에 넣는다. 비우거나 그대로면 취소 */
function askFormRename(btn) {
  var f = btn.form, opt = f.elements.tocFormSn.options[f.elements.tocFormSn.selectedIndex];
  if (!f.elements.tocFormSn.value) return true; // 양식을 안 골랐으면 브라우저가 "선택해 주세요"를 띄운다
  var old = opt.dataset.name || '', nm = prompt('"' + old + '" 양식의 새 이름을 입력해 주세요.', old);
  if (nm === null || !nm.trim() || nm.trim() === old) return false;
  f.elements.tocFormNm.value = nm.trim();
  return true;
}

/* 새 이름으로 복사해서 바꾸기: 고른 양식의 목차를 새 이름 양식으로 복사해 가져온다. 새 이름을 hidden tocFormNm에 넣고 import로 보낸다 */
function askFormCopy(btn) {
  var f = btn.form, sel = f.elements.tocFormSn, opt = sel.options[sel.selectedIndex];
  if (!sel.value) return true; // 양식을 안 골랐으면 브라우저가 "선택해 주세요"를 띄운다
  var old = opt.dataset.name || '', nm = prompt('"' + old + '" 양식을 복사해서 가져옵니다. 새 양식 이름을 입력해 주세요.', old + ' 복사');
  if (nm === null || !nm.trim()) return false;
  if (!confirm('현재 목차와 담당자 지정이 사라지고, 연결된 요구사항은 모두 미매핑이 됩니다. "' + nm.trim() + '" 양식으로 복사해서 바꿀까요?')) return false;
  f.elements.tocFormNm.value = nm.trim();
  return true;
}

function confirmFormDelete(btn) {
  var sel = btn.form.elements.tocFormSn, opt = sel.options[sel.selectedIndex];
  if (!sel.value) return true;
  return confirm('"' + (opt.dataset.name || '') + '" 양식을 삭제할까요? 이미 이 양식으로 만든 사업건 목차는 그대로 남습니다.');
}

/* 같은 이름의 양식이 이미 있으면 덮어쓸지 묻는다 (공백 차이는 같은 이름으로 본다). 이름 목록은 위 양식 선택칸에서 읽는다 */
function confirmFormSave(f) {
  var nm = f.elements.tocFormNm.value.trim().replace(/\s+/g, ' '), same = false;
  f.parentNode.querySelectorAll('.form-bar option[data-name]').forEach(function (o) { if (o.dataset.name === nm) same = true; });
  return !same || confirm('"' + nm + '"은(는) 이미 있는 양식 이름입니다. 그 양식을 지금 목차로 덮어쓸까요? 기존 양식 내용은 사라집니다.');
}

/* ---------- 목차 담당자: 제안팀원 / 외부 회사 (FR-049·FR-043-1) ---------- */

/* "+ 외부 회사 직접 입력"을 고르면 왼쪽 아래 "외부 회사 입력" 칸을 열고, 그 밖의 값은 바로 저장한다 */
var coSel = null; // 외부 회사를 입력 중인 담당자 선택칸

function pickOwner(sel) {
  var panel = document.getElementById('coPanel');
  if (sel.value === 'co-new' && panel) {
    if (coSel && coSel !== sel) restoreOwner(coSel); // 다른 줄에서 열려 있었으면 그 줄은 원래 값으로
    coSel = sel;
    document.getElementById('coTocSn').value = sel.form.elements.tocSn.value;
    document.getElementById('coTarget').textContent = sel.form.dataset.label || '';
    panel.hidden = false;
    var inp = document.getElementById('coNmInput');
    inp.value = ''; inp.focus();
    return;
  }
  if (panel) panel.hidden = true;
  coSel = null;
  saveNow(sel.form);
}

/* 선택칸을 처음 값으로 되돌린다 */
function restoreOwner(sel) {
  for (var i = 0; i < sel.options.length; i++) sel.options[i].selected = sel.options[i].defaultSelected;
  if (sel.value === 'co-new') sel.value = '';
  if (sel.syncPicker) sel.syncPicker();
}

/* 외부 회사 입력 취소: 칸을 닫고 그 줄의 선택값을 원래대로 */
function cancelOwner() {
  var panel = document.getElementById('coPanel');
  if (panel) panel.hidden = true;
  if (coSel) restoreOwner(coSel);
  coSel = null;
}

/* 담당자 선택창: 기본 선택창은 목록 길이를 브라우저가 정해 들쭉날쭉하므로, 같은 선택칸을 바탕으로
   높이가 정해진(.own-panel) 목록을 따로 그린다. 값은 원래 <select>에 넣고 change를 일으켜 위 pickOwner가 그대로 일한다. */
var ownPanel = null;

function closeOwnPanel() {
  if (ownPanel) { ownPanel.remove(); ownPanel = null; }
}

function openOwnPanel(sel, btn) {
  closeOwnPanel();
  var p = document.createElement('div');
  p.className = 'own-panel';
  Array.prototype.forEach.call(sel.children, function (ch) {
    var opts = ch.tagName === 'OPTGROUP' ? ch.children : [ch];
    if (ch.tagName === 'OPTGROUP') {
      var h = document.createElement('div');
      h.className = 'own-grp'; h.textContent = ch.label; p.appendChild(h);
    }
    Array.prototype.forEach.call(opts, function (o) {
      var it = document.createElement('div');
      it.className = 'own-item' + (o.selected ? ' on' : '');
      it.textContent = o.textContent;
      it.addEventListener('mousedown', function (e) {
        e.preventDefault();
        sel.value = o.value;
        closeOwnPanel();
        sel.syncPicker();
        sel.dispatchEvent(new Event('change')); // onchange="pickOwner(this)"
      });
      p.appendChild(it);
    });
  });
  document.body.appendChild(p);
  var r = btn.getBoundingClientRect(), below = window.innerHeight - r.bottom, h = Math.min(p.offsetHeight, 280);
  p.style.width = Math.max(r.width, 220) + 'px';
  p.style.left = Math.min(r.left, window.innerWidth - Math.max(r.width, 220) - 8) + 'px';
  p.style.top = (below >= h + 8 || r.top < h + 8 ? r.bottom + 2 : r.top - h - 2) + 'px';
  var on = p.querySelector('.own-item.on');
  if (on) p.scrollTop = Math.max(0, on.offsetTop - 80);
  p.openedAt = Date.now();
  ownPanel = p;
}

function initOwnerPickers() {
  document.querySelectorAll('.owner-form select[name="picId"]').forEach(function (sel) {
    var btn = document.createElement('button');
    btn.type = 'button'; btn.className = 'own-btn';
    sel.syncPicker = function () {
      var o = sel.options[sel.selectedIndex];
      btn.textContent = o ? o.textContent : '미지정';
    };
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      if (ownPanel) closeOwnPanel(); else openOwnPanel(sel, btn);
    });
    sel.style.display = 'none';
    sel.parentNode.insertBefore(btn, sel);
    sel.syncPicker();
  });
}
document.addEventListener('click', closeOwnPanel);
window.addEventListener('scroll', function (e) { // 목록 안을 스크롤하는 건 닫지 않는다
  // 열린 직후(0.4초)에 생기는 레이아웃 변화·포커스 이동 때문에 생기는 스크롤로는 닫지 않는다
  if (ownPanel && Date.now() - ownPanel.openedAt > 400 && e.target !== ownPanel && !ownPanel.contains(e.target)) closeOwnPanel();
}, true);
window.addEventListener('resize', function () { if (ownPanel && Date.now() - ownPanel.openedAt > 400) closeOwnPanel(); });
document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeOwnPanel(); });
onPageReady(function () { coSel = null; closeOwnPanel(); initOwnerPickers(); }); // 비동기 저장으로 목차 표가 바뀌면 다시 붙인다

/* ---------- 저장하지 않은 입력 경고 ---------- */

/* 폼을 보내는 중이면 경고하지 않는다.
   requestSubmit()은 submit 이벤트를 일으켜 비동기 저장(async-form.js)이 가로챌 수 있다. (submit()은 이벤트가 없다) */
var saving = false;
function saveNow(f) { saving = true; if (f.requestSubmit) f.requestSubmit(); else f.submit(); }
onPageReady(function () { saving = false; }); // 비동기 저장이 끝나 새 내용이 오면 다시 경고 대상
document.addEventListener('submit', function (e) { if (!e.defaultPrevented) saving = true; });

/* data-track 칸에 적고 저장하지 않은 채 나가려 하면 브라우저가 확인을 묻는다 */
window.addEventListener('beforeunload', function (e) {
  if (saving) return;
  var dirty = Array.prototype.some.call(document.querySelectorAll('[data-track]'), function (i) {
    return !i.disabled && i.value !== i.defaultValue;
  });
  if (dirty) { e.preventDefault(); e.returnValue = ''; }
});

/* ---------- 제안팀 인원 추가 (FR-043 v4.0, 왼쪽 아래 칸) ----------
   후보는 서버에서 20명씩 받아 온다(fetch, JSON). 목록 끝까지 스크롤하면 다음 20명을 이어 붙인다(무한 스크롤).
   팀·직급·이름을 바꾸면 처음부터 다시 받는다. 체크한 사람은 mp.picked에 기억해 두고, 보낼 때 hidden userId로 만든다. */
var mp = { offset: 0, hasMore: true, loading: false, seq: 0, picked: {}, timer: null };

/* 인원 추가 칸은 팝업이 아니라 왼쪽 아래에 늘 떠 있다. 화면이 열리면(비동기 저장 뒤 포함) 후보를 처음부터 불러온다 */
function initMemPop() {
  mp.picked = {};
  if (document.getElementById('memPop')) reloadMemPop();
}

/* 체크한 사람을 모두 해제 */
function clearMemPop() {
  mp.picked = {};
  document.querySelectorAll('#mp-body input[type="checkbox"]').forEach(function (c) { c.checked = false; });
  document.getElementById('mp-cnt').textContent = '0명 선택';
}

/* 필터가 바뀌면 목록을 비우고 처음부터 */
function reloadMemPop() {
  mp.offset = 0; mp.hasMore = true; mp.loading = false; mp.seq++;
  document.getElementById('mp-body').innerHTML = '';
  document.getElementById('mp-list').scrollTop = 0;
  loadMemPop();
}

/* 이름은 입력을 멈추고 0.3초 뒤에 찾는다 */
function searchMemPop() {
  clearTimeout(mp.timer);
  mp.timer = setTimeout(reloadMemPop, 300);
}

/* 목록 끝 근처까지 스크롤하면 다음 묶음 */
function moreMemPop() {
  var box = document.getElementById('mp-list');
  if (box.scrollTop + box.clientHeight >= box.scrollHeight - 40) loadMemPop();
}

function loadMemPop() {
  if (mp.loading || !mp.hasMore) return;
  var pop = document.getElementById('memPop'), seq = mp.seq, state = document.getElementById('mp-state');
  var p = new URLSearchParams({ bid: pop.dataset.bid, offset: mp.offset,
    teamSn: document.getElementById('mp-team').value, rank: document.getElementById('mp-rank').value,
    q: document.getElementById('mp-q').value.trim() });
  mp.loading = true;
  state.textContent = '불러오는 중…';
  fetch(pop.dataset.url + '?' + p.toString(), { headers: { 'Accept': 'application/json' } })
    .then(function (res) {
      if (!res.ok) throw new Error(res.status);
      return res.json();
    })
    .then(function (data) {
      if (seq !== mp.seq) return; // 그사이 필터가 바뀌었으면 버린다
      var body = document.getElementById('mp-body');
      data.items.forEach(function (u) { body.appendChild(memRow(u)); });
      mp.offset += data.items.length;
      mp.hasMore = data.hasMore;
      mp.loading = false;
      state.textContent = body.children.length === 0 ? '조건에 맞는 인원이 없습니다.' : '';
      // 첫 묶음이 칸을 다 채우지 못했는데 더 있으면 이어서 가져온다
      var box = document.getElementById('mp-list');
      if (mp.hasMore && box.scrollHeight <= box.clientHeight) loadMemPop();
    })
    .catch(function () {
      if (seq !== mp.seq) return;
      mp.loading = false;
      state.textContent = '목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
    });
}

/* 후보 한 줄. 이름 등은 textContent로 넣는다(HTML로 해석하지 않음) */
function memRow(u) {
  var tr = document.createElement('tr'), c = document.createElement('input');
  tr.className = 'mp-row';
  c.type = 'checkbox'; c.value = u.userId; c.checked = !!mp.picked[u.userId];
  c.setAttribute('aria-label', u.userNm + ' 선택');
  c.addEventListener('change', function () { pickMem(u, c.checked); });
  var td = document.createElement('td'); td.appendChild(c); tr.appendChild(td);
  var b = document.createElement('b'); b.textContent = u.userNm;
  td = document.createElement('td'); td.appendChild(b); tr.appendChild(td);
  [u.teamNm, u.jbgdNm || '-'].forEach(function (v) {
    var x = document.createElement('td'); x.textContent = v; tr.appendChild(x);
  });
  return tr;
}

function pickMem(u, on) {
  if (on) mp.picked[u.userId] = u.userNm; else delete mp.picked[u.userId];
  document.getElementById('mp-cnt').textContent = Object.keys(mp.picked).length + '명 선택';
}

/* 보낼 때 기억해 둔 사람을 hidden userId로 만든다 (필터로 안 보이는 사람도 포함) */
function checkMemPop(f) {
  var ids = Object.keys(mp.picked), box = document.getElementById('mp-picked');
  if (!ids.length) {
    alert('추가할 인원을 체크해 주세요.');
    return false;
  }
  box.innerHTML = '';
  ids.forEach(function (id) {
    var h = document.createElement('input');
    h.type = 'hidden'; h.name = 'userId'; h.value = id;
    box.appendChild(h);
  });
  return true;
}

/* 줄 아무 데나 눌러도 체크 */
document.addEventListener('click', function (e) {
  var row = e.target.closest && e.target.closest('#memPop .mp-row');
  if (row && e.target.tagName !== 'INPUT') {
    var c = row.querySelector('input[type="checkbox"]');
    c.checked = !c.checked;
    c.dispatchEvent(new Event('change'));
  }
});
onPageReady(initMemPop);

/* ---------- 요구사항 PDF 추출 미리보기 (FR-050-1) ---------- */

/* 머리 칸 체크박스로 전체 선택·해제 */
function toggleExtract(all) {
  document.querySelectorAll('.ex-pick').forEach(function (c) { c.checked = all.checked; });
}

/* ---------- 질의응답 팝업 (qa.html): 보기 · 추가 · 수정 ---------- */

var qaRow = null; // 지금 팝업에 띄운 목록 줄 (추가 중이면 null)

function qaEl(id) { return document.getElementById(id); }

/* 보기(view)와 입력(form) 중 하나만 보이게 하고 팝업을 연다 */
function qaShow(form, title) {
  qaEl('qaView').hidden = form;
  qaEl('qaForm').hidden = !form;
  qaEl('qaPopTitle').textContent = title;
  qaEl('qaPop').hidden = false;
  if (form) qaEl('qaFormQ').focus();
}

/* 목록 줄을 누르면 전체 질문·답변 보기. 현재 사업 Q&A(data-sn 있음)면 수정·삭제 버튼 */
function openQa(row, e) {
  if (e && e.target.closest('a, button, form, input, select, textarea')) return;
  if (!qaEl('qaPop')) return;
  qaRow = row;
  qaEl('qaPopQ').textContent = row.dataset.q || '';
  var a = row.dataset.a;
  qaEl('qaPopA').textContent = a ? a : '답변을 아직 적지 않았습니다.';
  qaEl('qaPopA').classList.toggle('faint', !a);
  var r = row.dataset.r;
  qaEl('qaPopR').textContent = r ? r : '-';
  qaEl('qaPopR').classList.toggle('faint', !r);
  var mine = !!row.dataset.sn;
  qaEl('qaViewAct').hidden = !mine;
  if (mine) {
    qaEl('qaDelSn').value = row.dataset.sn;
    qaEl('qaPopDel').hidden = row.dataset.del !== 'true';
  }
  qaShow(false, '질의응답 상세');
}

/* [+ 추가] 버튼: 빈 입력 칸 */
function addQa() {
  qaRow = null;
  var f = qaEl('qaForm');
  f.reset();
  f.action = f.dataset.add;
  qaEl('qaFormSn').disabled = true;
  qaEl('qaFormSubmit').textContent = '등록';
  qaShow(true, qaEl('qaPop').dataset.addTitle || '질의응답 추가');
}

/* 보기에서 [수정]: 같은 팝업에서 입력 칸으로 */
function editQa() {
  if (!qaRow) return;
  var f = qaEl('qaForm');
  f.reset();
  f.action = f.dataset.edit;
  qaEl('qaFormSn').disabled = false;
  qaEl('qaFormSn').value = qaRow.dataset.sn;
  qaEl('qaFormQ').value = qaRow.dataset.q || '';
  qaEl('qaFormA').value = qaRow.dataset.a || '';
  qaEl('qaFormR').value = qaRow.dataset.rsn || '';
  qaEl('qaFormSubmit').textContent = '저장';
  qaShow(true, '질의응답 수정');
}

/* 입력 중 [취소]: 수정이면 보기로 돌아가고, 추가면 닫는다 */
function cancelQaForm() {
  if (qaRow) openQa(qaRow);
  else closeQa();
}

function closeQa() {
  var pop = qaEl('qaPop');
  if (pop) pop.hidden = true;
}

document.addEventListener('keydown', function (e) {
  if (e.key === 'Escape') closeQa();
});

/* ---------- 파일 첨부 영역 (.drop): 눌러서 고르기 + 끌어 놓기 ---------- */

function dropSize(b) {
  return b >= 1048576 ? (b / 1048576).toFixed(1) + 'MB' : Math.max(1, Math.round(b / 1024)) + 'KB';
}

/* accept(".pdf,application/pdf")에 맞는 파일인지 */
function dropAccepts(accept, f) {
  if (!accept) return true;
  var name = f.name.toLowerCase(), type = (f.type || '').toLowerCase();
  return accept.split(',').some(function (a) {
    a = a.trim().toLowerCase();
    if (!a) return false;
    if (a.charAt(0) === '.') return name.slice(-a.length) === a;
    if (a.slice(-2) === '/*') return type.indexOf(a.slice(0, -1)) === 0;
    return type === a;
  });
}

/* 고른 파일 이름·크기를 보여 주고, 형식·용량이 맞지 않으면 비우고 안내한다 */
function dropRender(zone) {
  var input = zone.querySelector('input[type=file]');
  var text = zone.querySelector('.drop-text');
  var hint = zone.querySelector('.drop-hint');
  if (zone.dataset.text === undefined) {
    zone.dataset.text = text.innerHTML;
    zone.dataset.hint = hint ? hint.textContent : '';
  }
  var f = input.files && input.files[0];
  var max = Number(input.dataset.maxMb || 0);
  var err = null;
  if (f && !dropAccepts(input.accept, f)) err = '이 형식은 올릴 수 없습니다: ' + f.name;
  else if (f && max && f.size > max * 1048576) err = '최대 ' + max + 'MB까지 올릴 수 있습니다 (' + dropSize(f.size) + ')' + (input.accept ? '.' : '. ZIP으로 압축해 보세요.');
  if (err) {
    input.value = '';
    f = null;
  }
  zone.classList.toggle('err', !!err);
  zone.classList.toggle('has', !!f);
  if (f) {
    text.innerHTML = '';
    var nm = document.createElement('span');
    nm.className = 'nm';
    nm.textContent = f.name;
    var sz = document.createElement('span');
    sz.className = 'sz';
    sz.textContent = dropSize(f.size);
    text.appendChild(nm);
    text.appendChild(sz);
    zone.title = f.name;
  } else {
    text.innerHTML = zone.dataset.text;
    zone.removeAttribute('title');
  }
  if (hint) hint.textContent = err ? err : (f ? '다른 파일을 놓거나 눌러서 바꿀 수 있습니다' : zone.dataset.hint);
}

function dropZoneOf(e) {
  return e.target && e.target.closest ? e.target.closest('.drop') : null;
}

function dropHasFiles(e) {
  return e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') >= 0;
}

document.addEventListener('change', function (e) {
  var z = dropZoneOf(e);
  if (z && e.target.type === 'file') dropRender(z);
});
['dragenter', 'dragover'].forEach(function (t) {
  document.addEventListener(t, function (e) {
    if (!dropHasFiles(e)) return;
    e.preventDefault(); // 영역 밖에 놓아도 브라우저가 파일을 열어 버리지 않게
    var z = dropZoneOf(e);
    if (z) z.classList.add('over');
    if (e.dataTransfer) e.dataTransfer.dropEffect = z ? 'copy' : 'none';
  });
});
document.addEventListener('dragleave', function (e) {
  var z = dropZoneOf(e);
  if (z && !z.contains(e.relatedTarget)) z.classList.remove('over');
});
document.addEventListener('drop', function (e) {
  if (!dropHasFiles(e)) return;
  e.preventDefault();
  var z = dropZoneOf(e);
  if (!z) return;
  z.classList.remove('over');
  var input = z.querySelector('input[type=file]');
  if (e.dataTransfer.files.length) {
    var dt = new DataTransfer();
    dt.items.add(e.dataTransfer.files[0]); // 한 번에 한 파일
    input.files = dt.files;
    dropRender(z);
  }
});



/* ---------- 목차 표 보기 모드 / 수정 모드 ----------
   매핑이 다 끝난 사업건은 처음에 보기 모드로 연다(입력칸·선택창을 글자로 바꾸고 편집 버튼을 숨김).
   고른 모드는 이 탭을 닫기 전까지 사업건마다 기억한다(sessionStorage, 안 되는 환경에서는 기본값). */
function tocModeKey(w) { return 'tocMode:' + (w.dataset.bid || ''); }

function setTocMode(view) {
  var w = document.getElementById('tocWrap');
  if (!w) return;
  w.classList.toggle('view', view);
  w.querySelectorAll('.ro').forEach(function (x) { x.remove(); });
  w.querySelectorAll('[data-ro-hid]').forEach(function (x) { x.style.display = ''; x.removeAttribute('data-ro-hid'); });
  if (view) {
    w.querySelectorAll('.ts input[type="text"], .ts input[type="number"], .ts .own-btn').forEach(function (el) {
      if (el.closest('.rq-add-row') || el.closest('tr.addrow')) return;
      var text = el.classList.contains('own-btn') ? el.textContent : el.value;
      if (el.type === 'number') text = text + '쪽';
      var s = document.createElement('span');
      s.className = 'ro' + (el.classList.contains('bold') ? ' bold' : '') + (text === '미지정' ? ' faint' : '');
      s.textContent = text;
      el.style.display = 'none'; el.setAttribute('data-ro-hid', '1');
      el.insertAdjacentElement('afterend', s);
    });
  }
  var b = document.getElementById('tocModeBtn');
  if (b) b.textContent = view ? '수정 모드로 바꾸기' : '보기 모드로 바꾸기';
  try { sessionStorage.setItem(tocModeKey(w), view ? 'view' : 'edit'); } catch (e) { /* 저장 못 해도 괜찮다 */ }
}

function toggleTocMode() {
  var w = document.getElementById('tocWrap');
  if (w) setTocMode(!w.classList.contains('view'));
}

onPageReady(function () {
  var w = document.getElementById('tocWrap');
  if (!w || !document.getElementById('tocModeBtn')) return; // 수정할 수 없는 사람은 원래 글자로만 보인다
  var saved = null;
  try { saved = sessionStorage.getItem(tocModeKey(w)); } catch (e) { /* 무시 */ }
  setTocMode(saved ? saved === 'view' : w.dataset.done === 'true');
});
