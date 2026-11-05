/* 공고 상세(bid/notice-detail.html): 되돌리기 어려운 처리 확인
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 같은 자리에서 <script th:src> 로 불러온다. */
// 되돌리기 어려운 처리(불참 종결·공고 삭제·참여·불참)는 한 번 더 묻는다.
document.querySelectorAll('[data-confirm]').forEach(function (btn) {
  btn.addEventListener('click', function (e) {
    if (!window.confirm(btn.getAttribute('data-confirm'))) {
      e.preventDefault();
    }
  });
});
