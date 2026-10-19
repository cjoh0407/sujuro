/*
 * 공통 스크립트. 화면마다 필요한 스크립트는 각 페이지의 scripts 영역(layout:fragment="scripts")에 둔다.
 */
(function () {
  // 화면 오른쪽 아래에 짧은 안내 메시지를 보여 준다. 예: showToast('저장했습니다.')
  window.showToast = function (message) {
    var el = document.getElementById('toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('on');
    setTimeout(function () { el.classList.remove('on'); }, 2600);
  };

  // fetch/AJAX 요청에 붙일 CSRF 정보. 예: headers[csrf.header] = csrf.token
  var tokenMeta = document.querySelector('meta[name="_csrf"]');
  var headerMeta = document.querySelector('meta[name="_csrf_header"]');
  window.csrf = {
    token: tokenMeta ? tokenMeta.getAttribute('content') : null,
    header: headerMeta ? headerMeta.getAttribute('content') : null
  };
})();
