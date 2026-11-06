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

  window.onPageReady(function () {
    if (reduce) return;
    document.querySelectorAll('.dash-stat .v > span, .dash-kpi .v > span').forEach(countUp);
  });
})();
