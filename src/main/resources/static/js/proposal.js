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
  var r = prompt('삭제 사유를 입력하세요. (목록에서만 숨기고 원본과 기록은 남습니다)');
  if (r === null || !r.trim()) return false;
  f.elements.reason.value = r.trim();
  return true;
}

/* ---------- 목차 양식 (FR-048) ---------- */

/* 이름 바꾸기: 새 이름을 받아 hidden tocFormNm에 넣는다. 비우거나 그대로면 취소 */
function askFormRename(btn) {
  var f = btn.form, opt = f.elements.tocFormSn.options[f.elements.tocFormSn.selectedIndex];
  if (!f.elements.tocFormSn.value) return true; // 양식을 안 골랐으면 브라우저가 "선택하세요"를 띄운다
  var old = opt.dataset.name || '', nm = prompt('"' + old + '" 양식의 새 이름을 입력하세요.', old);
  if (nm === null || !nm.trim() || nm.trim() === old) return false;
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

/* "+ 외부 회사 직접 입력"을 고르면 입력칸을 열고, 그 밖의 값은 바로 저장한다 */
function pickOwner(sel) {
  var box = sel.form.querySelector('.co-new'), inp = box.querySelector('input');
  if (sel.value === 'co-new') {
    box.hidden = false; inp.disabled = false; inp.focus();
    return;
  }
  box.hidden = true; inp.disabled = true; inp.value = '';
  saveNow(sel.form);
}

/* 직접 입력 취소: 선택칸을 원래 값으로 되돌린다 */
function cancelOwner(btn) {
  var f = btn.form, sel = f.elements.picId, box = f.querySelector('.co-new'), inp = box.querySelector('input');
  inp.value = ''; inp.disabled = true; box.hidden = true;
  for (var i = 0; i < sel.options.length; i++) sel.options[i].selected = sel.options[i].defaultSelected;
  if (sel.value === 'co-new') sel.value = '';
}

/* ---------- 저장하지 않은 입력 경고 ---------- */

/* 폼을 보내는 중이면 경고하지 않는다. form.submit()은 submit 이벤트가 없으니 saveNow로 보낸다 */
var saving = false;
function saveNow(f) { saving = true; f.submit(); }
document.addEventListener('submit', function (e) { if (!e.defaultPrevented) saving = true; });

/* data-track 칸에 적고 저장하지 않은 채 나가려 하면 브라우저가 확인을 묻는다 */
window.addEventListener('beforeunload', function (e) {
  if (saving) return;
  var dirty = Array.prototype.some.call(document.querySelectorAll('[data-track]'), function (i) {
    return !i.disabled && i.value !== i.defaultValue;
  });
  if (dirty) { e.preventDefault(); e.returnValue = ''; }
});

/* ---------- 제안팀 인원 찾기 팝업 (FR-043 v4.0) ----------
   후보는 서버에서 20명씩 받아 온다(fetch, JSON). 목록 끝까지 스크롤하면 다음 20명을 이어 붙인다(무한 스크롤).
   팀·직급·이름을 바꾸면 처음부터 다시 받는다. 체크한 사람은 mp.picked에 기억해 두고, 보낼 때 hidden userId로 만든다. */
var mp = { offset: 0, hasMore: true, loading: false, seq: 0, picked: {}, timer: null };

function openMemPop() {
  var pop = document.getElementById('memPop');
  if (!pop) return;
  pop.hidden = false;
  if (!document.getElementById('mp-body').children.length) reloadMemPop();
  document.getElementById('mp-q').focus();
}

function closeMemPop() {
  var pop = document.getElementById('memPop');
  if (pop) pop.hidden = true;
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
      state.textContent = '목록을 불러오지 못했습니다. 잠시 후 다시 열어 주세요.';
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

/* 줄 아무 데나 눌러도 체크, Esc로 닫기 */
document.addEventListener('click', function (e) {
  var row = e.target.closest && e.target.closest('#memPop .mp-row');
  if (row && e.target.tagName !== 'INPUT') {
    var c = row.querySelector('input[type="checkbox"]');
    c.checked = !c.checked;
    c.dispatchEvent(new Event('change'));
  }
});
document.addEventListener('keydown', function (e) {
  if (e.key === 'Escape') closeMemPop();
});

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

