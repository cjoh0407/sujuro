/* 공고 목록(bid/notices.html): [지금 수집] 중복 클릭 방지
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 같은 자리에서 <script th:src> 로 불러온다. */
// 주관팀 필터를 고르면 바로 검색 폼을 보내는 동작은 공통(common.js)의 data-change-submit 이 맡는다.

// [지금 수집] 은 끝날 때까지 수십 초 걸릴 수 있다. 두 번 눌리지 않게 막고 진행 중임을 보여 준다.
// 버튼 글자는 화면(notices.html)에 적힌 것을 기억해 두었다가 되돌린다 (11/12 '수집' 으로 바꾼 글자가 덮어써지던 문제)
document.querySelectorAll('[data-collect-form]').forEach(function (form) {
  var btn = form.querySelector('button');
  btn.dataset.label = btn.textContent;
  form.addEventListener('submit', function () {
    btn.disabled = true;
    btn.textContent = '수집 중…';
  });
});

// 결과를 본 뒤 [뒤로 가기] 로 돌아오면 브라우저가 누르던 순간의 화면(막힌 버튼)을 그대로 되살린다.
// 화면이 다시 보일 때마다 버튼을 풀어 둔다. 수집이 정말 돌고 있으면 서버가 '진행 중' 으로 막는다.
window.addEventListener('pageshow', function () {
  document.querySelectorAll('[data-collect-form] button').forEach(function (btn) {
    btn.disabled = false;
    if (btn.dataset.label) { btn.textContent = btn.dataset.label; }
  });
});

/* 공고 등록 번호 검사 (11/10): 형식만 본다 (R + 연도 2자리 + BK·BD·DD + 숫자). 자릿수는 묻지 않는다.
   형식이 맞는데 나라장터에 없는 번호는 서버가 "찾을 수 없습니다" 빨간 알림을 띄운다 */
function checkPbancNo(f) {
  var no = (f.elements.pbancNo.value || '').replace(/\s+/g, '').toUpperCase();
  var msg = null;
  if (!no) {
    msg = '공고번호를 입력해 주세요.';
  } else if (!/^R\d{2}(BK|BD|DD)\d+$/.test(no)) {
    msg = '공고번호 형식이 아닙니다. 발주계획(R26DD…), 사전규격(R26BD…), 입찰공고(R26BK…) 번호를 넣어 주세요.';
  }
  if (msg) {
    if (window.showToast) { window.showToast(msg, 'err'); }
    f.elements.pbancNo.focus();
    return false;
  }
  return true;
}
