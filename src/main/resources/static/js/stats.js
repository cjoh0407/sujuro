/* 사업 이력·통계(stats/index.html): 사업 목록 스크롤, 연도·팀 비동기 전환
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 같은 자리에서 <script th:src> 로 불러온다. */
(function () {
  // 사업 목록은 칸 안에서 스크롤한다. 고른 사업이 칸 밖에 있으면 보이는 자리로 옮긴다
  function showPickedBid() {
    var on = document.querySelector('.bid-body .bid-item.on');
    if (!on) {
      return;
    }
    var box = on.parentNode;
    var row = on.getBoundingClientRect();
    var view = box.getBoundingClientRect();
    if (row.bottom > view.bottom || row.top < view.top) {
      box.scrollTop += row.top - view.top;
    }
  }
  showPickedBid();

  // 머리와 본문을 받아 온 것으로 바꾼다. 이 화면이 아니면(로그인 만료 등) false
  function swap(doc) {
    var head = doc.querySelector('.page-head');
    var split = doc.querySelector('.split');
    if (!head || !split) {
      return false;
    }
    document.querySelector('.page-head').replaceWith(head);
    document.querySelector('.split').replaceWith(split);
    showPickedBid();
    return true;
  }

  var last = 0; // 빠르게 여러 번 고르면 마지막 요청만 화면에 넣는다

  // 연도·팀을 바꾼 화면을 페이지를 새로 열지 않고 보여 준다. 실패하면 보통 방식으로 연다(로그인 만료면 로그인 화면으로 간다)
  function load(url, push, focusTarget) {
    var mine = ++last;
    fetch(url).then(function (res) {
      if (!res.ok) {
        throw new Error('HTTP ' + res.status);
      }
      return res.text();
    }).then(function (html) {
      if (mine !== last) {
        return;
      }
      if (!swap(new DOMParser().parseFromString(html, 'text/html'))) {
        throw new Error('사업 이력 화면이 아닙니다');
      }
      if (push && new URL(url, location.href).href !== location.href) {
        history.pushState(null, '', url);
      }
      if (focusTarget) {
        document.querySelector(focusTarget).focus({ preventScroll: true });
      }
    }).catch(function () {
      if (mine === last) {
        location.assign(url);
      }
    });
  }

  // 왼쪽 연도·팀 드롭다운. 고르면 바로 바뀌고, 포커스는 고른 칸에 남는다.
  // 본문을 통째로 바꾸므로 이벤트는 document 에서 받는다
  document.addEventListener('change', function (e) {
    var form = e.target.closest('.scope-form');
    if (form) {
      load(form.action + '?' + new URLSearchParams(new FormData(form)), true, '#' + e.target.id);
    }
  });

  // 뒤로·앞으로 가기는 그 주소의 화면으로 바꾼다
  window.addEventListener('popstate', function () {
    load(location.href, false);
  });
})();
