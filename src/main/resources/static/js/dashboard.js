/* 대시보드(dashboard/index.html): 숫자 올라가기
 * 차트가 차오르는 효과는 dashboard.css 의 애니메이션이 한다. 여기서는 숫자만 0부터 세어 올린다.
 * onPageReady 라서 처음 열 때와 비동기로 화면이 바뀐 뒤 모두 실행된다. */
(function () {
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var DURATION = 900;

  function countUp(el) {
    var text = el.textContent.trim();
    if (!/^[0-9][0-9,]*(\.[0-9]+)?$/.test(text)) return;       // "-", "49.3억" 같은 값은 그대로 둔다
    var target = parseFloat(text.replace(/,/g, ''));
    if (!(target > 0)) return;
    var dec = (text.split('.')[1] || '').length;
    var comma = text.indexOf(',') >= 0;
    var start = null;
    function fmt(v) {
      var s = v.toFixed(dec);
      return comma ? Number(s).toLocaleString('ko-KR', { minimumFractionDigits: dec, maximumFractionDigits: dec }) : s;
    }
    el.textContent = fmt(0);
    function step(ts) {
      if (start === null) start = ts;
      var t = Math.min(1, (ts - start) / DURATION);
      var eased = 1 - Math.pow(1 - t, 3);                      // 처음엔 빠르게, 끝에서 천천히
      el.textContent = fmt(target * eased);
      if (t < 1) requestAnimationFrame(step); else el.textContent = text;
    }
    requestAnimationFrame(step);
  }

  // 미니 캘린더 화살표·[오늘] (11/07): 그 달의 대시보드를 불러와 캘린더만 바꾼다. 실패하면 그 주소로 이동한다.
  // 캘린더를 통째로 바꾸므로 클릭은 document 에서 받는다
  var last = 0;
  document.addEventListener('click', function (e) {
    var a = e.target.closest('a[data-cal-nav]');
    if (!a || e.metaKey || e.ctrlKey || e.shiftKey) return;
    var cal = a.closest('.dash-cal');
    if (!cal) return;
    e.preventDefault();
    var url = a.getAttribute('href');
    var mine = ++last;
    cal.classList.add('loading');
    fetch(url, { headers: { 'X-Requested-With': 'fetch' } }).then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.text();
    }).then(function (html) {
      if (mine !== last) return;
      var next = new DOMParser().parseFromString(html, 'text/html').querySelector('.dash-cal');
      if (!next) throw new Error('캘린더가 없습니다');
      cal.replaceWith(next);
      var focus = next.querySelector(a.getAttribute('title') ? 'a[title="' + a.getAttribute('title') + '"]' : 'h3');
      if (focus) focus.focus({ preventScroll: true });
    }).catch(function () {
      if (mine === last) location.assign(url);
    });
  });

  // 미니 캘린더: 일정이 있는 날을 누르면 그날 일정 팝업 (11/07). 칸 안의 숨은 목록(.cal-src)을 옮겨 담는다.
  // 바깥을 누르거나 Esc 로 닫는다. 캘린더가 바뀌면(월 이동) 팝업도 같이 사라진다
  var DOW = ['일', '월', '화', '수', '목', '금', '토'];
  function closePop() {
    document.querySelectorAll('.cal-pop').forEach(function (p) { p.remove(); });
    document.querySelectorAll('.dash-cal-cell.on').forEach(function (c) { c.classList.remove('on'); });
  }
  function openPop(cell) {
    var cal = cell.closest('.dash-cal');
    var src = cell.querySelector('.cal-src');
    if (!cal || !src) return;
    closePop();
    var ym = cal.getAttribute('data-ym') || '';
    var day = Number(cell.getAttribute('data-day'));
    var d = new Date(Number(ym.slice(0, 4)), Number(ym.slice(5, 7)) - 1, day);
    var items = src.querySelectorAll('li');
    var pop = document.createElement('div');
    pop.className = 'cal-pop';
    pop.setAttribute('role', 'dialog');
    var head = document.createElement('div');
    head.className = 'cal-pop-head';
    var t = document.createElement('b');
    t.textContent = (d.getMonth() + 1) + '월 ' + day + '일 (' + DOW[d.getDay()] + ') · 일정 ' + items.length + '건';
    var x = document.createElement('button');
    x.type = 'button'; x.className = 'x'; x.setAttribute('aria-label', '닫기'); x.textContent = '×';
    x.addEventListener('click', closePop);
    head.appendChild(t); head.appendChild(x);
    pop.setAttribute('aria-label', t.textContent);
    var ul = document.createElement('ul');
    items.forEach(function (li) { ul.appendChild(li.cloneNode(true)); });
    var foot = document.createElement('div');
    foot.className = 'cal-pop-foot';
    var link = cal.querySelector('a[href*="/schedule"]');
    if (link) {
      var a = document.createElement('a');
      a.href = link.getAttribute('href'); a.textContent = '일정 관리에서 보기 ›';
      foot.appendChild(a);
    }
    pop.appendChild(head); pop.appendChild(ul); if (link) pop.appendChild(foot);
    cal.appendChild(pop);
    // 위치: 누른 칸 바로 아래, 캘린더 밖으로 나가지 않게. 아래 자리가 모자라면 칸 위로
    var cr = cal.getBoundingClientRect(), r = cell.getBoundingClientRect();
    var left = Math.min(Math.max(8, r.left - cr.left + r.width / 2 - pop.offsetWidth / 2), cr.width - pop.offsetWidth - 8);
    var top = r.bottom - cr.top + 4;
    if (top + pop.offsetHeight > cr.height && r.top - cr.top - pop.offsetHeight - 4 > 0) top = r.top - cr.top - pop.offsetHeight - 4;
    pop.style.left = Math.max(8, left) + 'px';
    pop.style.top = top + 'px';
    cell.classList.add('on');
    x.focus({ preventScroll: true });
  }
  document.addEventListener('click', function (e) {
    var cell = e.target.closest('.dash-cal-cell.has');
    if (cell) { openPop(cell); return; }
    if (!e.target.closest('.cal-pop')) closePop();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') { closePop(); return; }
    var cell = e.target.closest && e.target.closest('.dash-cal-cell.has');
    if (cell && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); openPop(cell); }
  });

  window.onPageReady(function () {
    if (reduce) return;
    document.querySelectorAll('.dash-stat .v > span, .dash-kpi .v > span').forEach(countUp);
  });
})();
