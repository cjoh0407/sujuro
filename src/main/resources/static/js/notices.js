/* 공고 목록(bid/notices.html): [지금 수집] 중복 클릭 방지
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 같은 자리에서 <script th:src> 로 불러온다. */
// 주관팀 필터를 고르면 바로 검색 폼을 보내는 동작은 공통(common.js)의 data-change-submit 이 맡는다.

// [지금 수집] 은 끝날 때까지 수십 초 걸릴 수 있다. 두 번 눌리지 않게 막고 진행 중임을 보여 준다.
document.querySelectorAll('[data-collect-form]').forEach(function (form) {
  form.addEventListener('submit', function () {
    var btn = form.querySelector('button');
    btn.disabled = true;
    btn.textContent = '수집 중…';
  });
});

// 결과를 본 뒤 [뒤로 가기] 로 돌아오면 브라우저가 누르던 순간의 화면(막힌 버튼)을 그대로 되살린다.
// 화면이 다시 보일 때마다 버튼을 풀어 둔다. 수집이 정말 돌고 있으면 서버가 '진행 중' 으로 막는다.
window.addEventListener('pageshow', function () {
  document.querySelectorAll('[data-collect-form] button').forEach(function (btn) {
    btn.disabled = false;
    btn.textContent = '지금 수집';
  });
});
