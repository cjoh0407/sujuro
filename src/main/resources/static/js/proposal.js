/* 제안 업무 화면 공통 스크립트 (templates/proposal: 목차·요구사항, 제안서 파일, 제출서류) */

/* "+ 하위" / "+ 같은 단계" 입력 줄을 연다. 버튼의 data-t(목차), data-m(d: 하위, s: 같은 단계), data-label, data-depth */
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
  btn.closest('tr').hidden = true;
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
