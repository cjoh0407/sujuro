/* 공고 상세(bid/notice-detail.html): 이력 스크롤, 배정·결정 결과 알림
 * 되돌리기 어려운 처리 전 확인은 버튼의 data-confirm 을 공통(common.js)이 처리한다. */

// 이력은 오래된 순이라 최신·다음 할 일이 맨 아래다. 길어서 스크롤이 생기면 처음에 맨 아래를 보여 준다 (11/05)
document.querySelectorAll('.nd-scroll').forEach(function (el) {
  el.scrollTop = el.scrollHeight;
});

// 배정·결정·철회 결과는 오른쪽 아래 작은 알림으로. 실패(data-err)는 빨간 알림으로 조금 더 오래 보인다
document.addEventListener('DOMContentLoaded', function () {
  var box = document.getElementById('teamFlash');
  if (!box || !window.showToast) return;
  var err = box.getAttribute('data-err'), ok = box.getAttribute('data-ok');
  if (err) window.showToast(err, 'err');
  else if (ok) window.showToast(ok);
});
