/*
 * 공통 스크립트. 화면마다 필요한 스크립트는 각 페이지의 scripts 영역(layout:fragment="scripts")에 둔다.
 */
(function () {
  // 화면 오른쪽 아래에 짧은 안내 메시지를 보여 준다. 예: showToast('저장했습니다.'), showToast('저장하지 못했습니다.', 'err')
  // 오류(err)는 빨간 바탕으로 조금 더 오래 보여 준다.
  var toastTimer = null;
  window.showToast = function (message, kind) {
    var el = document.getElementById('toast');
    if (!el || !message) return;
    el.textContent = message;
    el.classList.toggle('err', kind === 'err');
    el.classList.toggle('warn', kind === 'warn');
    el.classList.toggle('info', kind === 'info');
    el.classList.add('on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('on'); }, (kind === 'err' || kind === 'warn') ? 5000 : 3000);
  };

  /* 안내 띠 대신 색 토스트 (11/09). 화면에 <div hidden data-flash="error|warn|info">문구</div> 를 두면
     화면이 열릴 때 오른쪽 아래에 띄운다(빨강·주황·파랑). 여러 개면 한 토스트에 줄을 나눠 보이고, 색은 가장 센 것.
     비동기로 화면 일부가 바뀔 때는 async-form.js 가 같은 규칙으로 띄운다 */
  window.flashToast = function (list) {
    if (!list || !list.length) return;
    var rank = { error: 3, warn: 2, info: 1 }, top = 'info';
    list.forEach(function (m) { if ((rank[m.kind] || 1) > rank[top]) top = m.kind; });
    var text = list.map(function (m) { return m.text; }).filter(Boolean).join('\n');
    window.showToast(text, top === 'error' ? 'err' : top);
  };
  window.readFlashes = function (root) {
    var out = [];
    Array.prototype.forEach.call(root.querySelectorAll('[data-flash]'), function (b) {
      out.push({ text: (b.innerText || b.textContent).trim(), kind: b.getAttribute('data-flash') || 'info' });
      b.remove();
    });
    return out;
  };
  function firstFlash() { window.flashToast(window.readFlashes(document)); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', firstFlash); else firstFlash();

  // fetch/AJAX 요청에 붙일 CSRF 정보. 예: headers[csrf.header] = csrf.token
  var tokenMeta = document.querySelector('meta[name="_csrf"]');
  var headerMeta = document.querySelector('meta[name="_csrf_header"]');
  window.csrf = {
    token: tokenMeta ? tokenMeta.getAttribute('content') : null,
    header: headerMeta ? headerMeta.getAttribute('content') : null
  };

  // 화면 내용에 이벤트를 붙이는 함수는 onPageReady(함수)로 등록한다.
  // 처음 화면이 열릴 때 한 번, 그리고 비동기 저장(async-form.js)으로 내용이 바뀔 때마다 다시 불린다.
  // (DOMContentLoaded 에 직접 붙이면 내용이 바뀐 뒤에는 이벤트가 사라진다)
  var readyFns = [];
  window.onPageReady = function (fn) {
    readyFns.push(fn);
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
    else fn();
  };
  window.runPageReady = function () {
    readyFns.forEach(function (fn) {
      try { fn(); } catch (e) { if (window.console) console.error(e); }
    });
  };
})();

/* ---------- 확인·입력·알림 창 ----------
   브라우저 기본 confirm/prompt/alert 창은 주소(3.35.131.20 내용:)가 같이 보여서, 화면 가운데에 우리 모양의 창으로 바꿔 띄운다.
   기존 코드(onsubmit="return confirm(...)", prompt(...))는 그대로 쓴다.
   기본 창은 코드를 멈추고 답을 기다리지만 이 창은 그럴 수 없으므로, 처음 부를 때는 창을 띄우고 "취소"로 돌려보낸 뒤
   사용자가 확인하면 같은 동작(제출·클릭)을 다시 실행하면서 저장해 둔 답을 돌려준다. */
(function () {
  var nativeConfirm = window.confirm, nativePrompt = window.prompt;
  var st = null;      // {answers, idx, replay}: 한 동작 안에서 받은 답들
  var busy = false;   // 창이 열려 있는 동안

  function ctx() {
    var ev = window.event;
    if (!ev) return null;
    var el = ev.currentTarget;
    if (!el || el === document || el === window) {
      // document 에서 받은 동작(data-confirm 등): 실제로 누른 폼·버튼·링크를 다시 실행한다
      el = ev.target && ev.target.closest ? (ev.target.closest('form, button, a, [data-confirm], [data-on-click]') || ev.target) : ev.target;
    }
    if (!el || !(ev.type === 'submit' || ev.type === 'click') || !el.nodeType) return null;
    return { ev: ev, el: el, submitter: ev.submitter || null };
  }

  function rerun(c) {
    st.replay = true; st.idx = 0;
    try {
      if (c.ev.type === 'submit' && c.el.requestSubmit) {
        var sub = c.submitter && c.submitter.form === c.el ? c.submitter : undefined;
        c.el.requestSubmit(sub);
      } else {
        c.el.click();
      }
    } finally {
      if (st && st.replay) st = null; // 끝까지 돌았으면 정리. 다음 질문을 기다리는 중이면(replay=false) 남겨 둔다
    }
  }

  function show(kind, msg, def, done) {
    busy = true;
    var ov = document.createElement('div');
    ov.className = 'dlg-ov';
    ov.setAttribute('role', 'presentation');
    var box = document.createElement('div');
    box.className = 'dlg';
    box.setAttribute('role', kind === 'alert' ? 'alertdialog' : 'dialog');
    box.setAttribute('aria-modal', 'true');
    var text = msg == null ? '' : String(msg);
    var danger = kind === 'confirm' && /삭제|지울까요|뺄까요|종결|철회|비활성|사라지고|덮어쓸/.test(text);
    box.className = 'dlg' + (danger ? ' danger' : '');
    var ic = document.createElement('div');
    ic.className = 'dlg-ic';
    ic.textContent = kind === 'prompt' ? '✎' : (danger ? '!' : (kind === 'alert' ? 'i' : '?'));
    box.appendChild(ic);
    var ti = document.createElement('div');
    ti.className = 'dlg-title';
    ti.textContent = kind === 'prompt' ? '내용을 입력해 주세요' : (kind === 'alert' ? '알림' : (danger ? '정말 진행할까요?' : '확인해 주세요'));
    box.appendChild(ti);
    var p = document.createElement('div');
    p.className = 'dlg-msg';
    p.textContent = text;
    box.appendChild(p);
    var input = null;
    if (kind === 'prompt') {
      input = document.createElement('input');
      input.type = 'text';
      input.className = 'dlg-in';
      input.value = def == null ? '' : def;
      box.appendChild(input);
    }
    var bar = document.createElement('div');
    bar.className = 'dlg-bar';
    var cancel = null, ok = document.createElement('button');
    ok.type = 'button';
    ok.className = 'btn primary dlg-ok';
    ok.textContent = '확인';
    if (kind !== 'alert') {
      cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'btn dlg-cancel';
      cancel.textContent = '취소';
      bar.appendChild(cancel);
    }
    bar.appendChild(ok);
    box.appendChild(bar);
    ov.appendChild(box);
    document.body.appendChild(ov);

    var prevFocus = document.activeElement;
    function close(val) {
      document.removeEventListener('keydown', onKey, true);
      if (ov.parentNode) ov.parentNode.removeChild(ov);
      busy = false;
      if (prevFocus && prevFocus.focus) { try { prevFocus.focus(); } catch (e) {} }
      if (done) done(val);
    }
    function accept() { close(kind === 'prompt' ? input.value : true); }
    function reject() { close(kind === 'prompt' ? null : false); }
    function onKey(e) {
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); reject(); }
      else if (e.key === 'Enter') { e.preventDefault(); e.stopPropagation(); accept(); }
    }
    document.addEventListener('keydown', onKey, true);
    ok.addEventListener('click', accept);
    if (cancel) cancel.addEventListener('click', reject);
    ov.addEventListener('mousedown', function (e) { if (e.target === ov && kind !== 'alert') reject(); });
    (input || ok).focus();
    if (input) input.select();
  }

  function ask(kind, msg, def) {
    var empty = kind === 'confirm' ? false : null;
    var c = ctx();
    if (!c || !document.body) return kind === 'confirm' ? nativeConfirm(msg) : nativePrompt(msg, def);
    if (st && st.replay) {
      if (st.idx < st.answers.length) return st.answers[st.idx++];
      st.replay = false; // 이어서 다음 질문이 나왔다: 이번 실행은 접고 새 답을 받는다
    } else if (!st || !busy) {
      st = { answers: [], idx: 0, replay: false };
    }
    if (busy) return empty;
    show(kind, msg, def, function (val) {
      if (val === null || val === false) { st = null; return; }
      st.answers.push(val);
      rerun(c);
    });
    return empty;
  }

  window.confirm = function (msg) { return ask('confirm', msg); };
  window.prompt = function (msg, def) { return ask('prompt', msg, def); };
  window.alert = function (msg) {
    if (!document.body) return;
    if (!busy) show('alert', msg);
  };
})();

/* ===== 화면의 동작 연결 (인라인 onclick/onsubmit 대신 data- 속성을 쓴다) =====
 * 이벤트는 document 에서 받으므로 비동기로 화면이 통째로 바뀌어도 다시 연결할 필요가 없다.
 *   data-confirm="문구"        폼이면 보낼 때, 버튼·링크면 누를 때 확인창을 띄운다. 취소하면 멈춘다.
 *   data-on-click="함수이름"   누르면 window.함수이름(요소, 이벤트) 를 부른다. false 를 돌려주면 기본 동작을 막는다.
 *   data-on-submit / data-on-change / data-on-input   위와 같고 이벤트만 다르다.
 *   data-self-click="함수이름"  요소 자신(바깥 배경 등)을 눌렀을 때만 부른다.
 *   data-arg="값"              있으면 함수의 첫 번째 인자로 요소 대신 이 값을 넘긴다.
 *   data-change-submit         값이 바뀌면 그 폼을 바로 보낸다.
 * 캡처 단계에서 받아 async-form.js 의 제출 처리보다 먼저 실행된다. */
(function () {
  function run(el, name, e) {
    var fn = window[name];
    if (typeof fn !== 'function') { return undefined; }
    var arg = el.getAttribute('data-arg');
    return fn.call(el, arg !== null ? arg : el, e, el);
  }
  function target(e, attr) {
    return e.target && e.target.closest ? e.target.closest('[' + attr + ']') : null;
  }

  ['click', 'submit', 'change', 'input'].forEach(function (type) {
    document.addEventListener(type, function (e) {
      var el = target(e, 'data-on-' + type);
      if (el && run(el, el.getAttribute('data-on-' + type), e) === false) { e.preventDefault(); }
    }, true);
  });

  document.addEventListener('click', function (e) {
    var el = target(e, 'data-self-click');
    if (el && e.target === el) { run(el, el.getAttribute('data-self-click'), e); }
  }, true);

  function confirmed(e, el) {
    if (!window.confirm(el.getAttribute('data-confirm'))) { e.preventDefault(); e.stopPropagation(); }
  }
  document.addEventListener('submit', function (e) {
    var el = e.target;
    if (el && el.hasAttribute && el.hasAttribute('data-confirm')) { confirmed(e, el); }
  }, true);
  document.addEventListener('click', function (e) {
    var el = target(e, 'data-confirm');
    if (el && !(el instanceof HTMLFormElement)) { confirmed(e, el); }
  }, true);

  document.addEventListener('change', function (e) {
    var el = target(e, 'data-change-submit');
    if (el && el.form) { el.form.requestSubmit(); }
  });
})();

/* ---------- 표 줄 전체 클릭 (data-row-link) ----------
   <tr data-row-link> 줄의 아무 데나 누르면 그 줄 안 첫 링크로 간다. 값을 주면 그 선택자의 링크 (예: data-row-link="a.sl").
   줄 안의 버튼·입력칸·선택칸·링크를 직접 누른 경우와 글자를 드래그로 고른 경우는 건드리지 않는다.
   Ctrl·Cmd·가운데 버튼은 새 탭으로 연다. */
(function () {
  function target(e) {
    var row = e.target.closest && e.target.closest('tr[data-row-link]');
    if (!row) return null;
    if (e.target.closest('a, button, input, select, textarea, label, form')) return null;
    var sel = window.getSelection && String(window.getSelection());
    if (sel) return null;
    return row.querySelector(row.getAttribute('data-row-link') || 'a[href]');
  }
  document.addEventListener('click', function (e) {
    if (e.button !== 0) return;
    var a = target(e);
    if (!a) return;
    if (e.ctrlKey || e.metaKey || e.shiftKey) { window.open(a.href, '_blank'); return; }
    a.click();
  });
  document.addEventListener('auxclick', function (e) {
    if (e.button !== 1) return;
    var a = target(e);
    if (a) window.open(a.href, '_blank');
  });
})();

/* ---------- 사업 드롭다운 펼친 목록 (11/09) ----------
   .bid-select / .sch-pick 의 <select>를 우리 모양 목록으로 보여 준다. 원래 select는 숨겨서 그대로 두고
   고르면 값을 넣고 change를 보낸다(data-change-submit가 그대로 폼을 보낸다).
   option의 data-name·data-sub가 있으면 이름과 상태를 나눠 보여 준다. 키보드: ↑↓ Home End Enter Esc */
(function () {
  var CHECK = '<svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 7.2 5.8 10 11 4.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var uid = 0;
  function esc(t) { var d = document.createElement('div'); d.textContent = t == null ? '' : t; return d.innerHTML; }
  function parts(o) {
    return { name: o.getAttribute('data-name') || o.textContent.trim(), sub: o.getAttribute('data-sub') || '' };
  }

  function enhance(sel) {
    if (sel.dataset.csel) return;
    sel.dataset.csel = '1';
    var id = 'csel' + (++uid);
    var wrap = document.createElement('div');
    wrap.className = 'csel';
    sel.parentNode.insertBefore(wrap, sel);
    wrap.appendChild(sel);
    sel.classList.add('csel-native');
    sel.tabIndex = -1;
    sel.setAttribute('aria-hidden', 'true');

    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'csel-btn';
    btn.setAttribute('aria-haspopup', 'listbox');
    btn.setAttribute('aria-expanded', 'false');
    btn.setAttribute('aria-controls', id);
    if (sel.getAttribute('aria-label')) btn.setAttribute('aria-label', sel.getAttribute('aria-label'));
    var pop = document.createElement('div');
    pop.className = 'csel-pop';
    pop.id = id;
    pop.setAttribute('role', 'listbox');
    pop.hidden = true;
    wrap.appendChild(btn);
    wrap.appendChild(pop);

    function paintBtn() {
      var o = sel.options[sel.selectedIndex];
      var p = o ? parts(o) : { name: '', sub: '' };
      btn.innerHTML = '<span class="csel-v">' + esc(p.name) + '</span>' + (p.sub ? '<span class="csel-sub">' + esc(p.sub) + '</span>' : '');
      btn.title = o ? o.textContent.trim() : '';
    }
    function build() {
      var html = '';
      Array.prototype.forEach.call(sel.children, function (n) {
        if (n.tagName === 'OPTGROUP') {
          html += '<div class="csel-grp">' + esc(n.label) + '<span>' + n.children.length + '</span></div>';
          Array.prototype.forEach.call(n.children, function (o) { html += item(o); });
        } else if (n.tagName === 'OPTION') { html += item(n); }
      });
      pop.innerHTML = html;
    }
    function item(o) {
      var p = parts(o), on = o.selected;
      return '<div class="csel-opt' + (on ? ' on' : '') + '" role="option" aria-selected="' + on + '" data-i="' + o.index + '" title="' + esc(o.textContent.trim()) + '">' +
        '<span class="csel-ck">' + (on ? CHECK : '') + '</span><span class="csel-nm">' + esc(p.name) + '</span>' +
        (p.sub ? '<span class="csel-sub">' + esc(p.sub) + '</span>' : '') + '</div>';
    }
    function opts() { return pop.querySelectorAll('.csel-opt'); }
    function setActive(el) {
      Array.prototype.forEach.call(opts(), function (x) { x.classList.toggle('act', x === el); });
      if (!el) return; // 화면 전체가 아니라 목록 안에서만 스크롤
      if (el.offsetTop < pop.scrollTop) pop.scrollTop = el.offsetTop - 6;
      else if (el.offsetTop + el.offsetHeight > pop.scrollTop + pop.clientHeight) pop.scrollTop = el.offsetTop + el.offsetHeight - pop.clientHeight + 6;
    }
    function open() {
      build();
      pop.hidden = false;
      wrap.classList.add('open');
      btn.setAttribute('aria-expanded', 'true');
      setActive(pop.querySelector('.csel-opt.on') || opts()[0]);
    }
    function close(focus) {
      if (pop.hidden) return;
      pop.hidden = true;
      wrap.classList.remove('open');
      btn.setAttribute('aria-expanded', 'false');
      if (focus) btn.focus();
    }
    function choose(el) {
      var i = +el.getAttribute('data-i');
      close(true);
      if (i === sel.selectedIndex) return;
      sel.selectedIndex = i;
      paintBtn();
      sel.dispatchEvent(new Event('change', { bubbles: true }));
    }

    btn.addEventListener('click', function () { if (pop.hidden) open(); else close(); });
    pop.addEventListener('mousedown', function (e) { e.preventDefault(); }); // 버튼 포커스 유지
    pop.addEventListener('click', function (e) {
      var el = e.target.closest('.csel-opt');
      if (el) choose(el);
    });
    pop.addEventListener('mousemove', function (e) {
      var el = e.target.closest('.csel-opt');
      if (el && !el.classList.contains('act')) Array.prototype.forEach.call(opts(), function (x) { x.classList.toggle('act', x === el); });
    });
    btn.addEventListener('keydown', function (e) {
      var list = Array.prototype.slice.call(opts());
      var cur = pop.querySelector('.csel-opt.act');
      var i = list.indexOf(cur);
      if (pop.hidden) {
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); open(); }
        return;
      }
      if (e.key === 'ArrowDown') { e.preventDefault(); setActive(list[Math.min(i + 1, list.length - 1)]); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(list[Math.max(i - 1, 0)]); }
      else if (e.key === 'Home') { e.preventDefault(); setActive(list[0]); }
      else if (e.key === 'End') { e.preventDefault(); setActive(list[list.length - 1]); }
      else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (cur) choose(cur); }
      else if (e.key === 'Escape' || e.key === 'Tab') { close(e.key === 'Escape'); }
    });
    document.addEventListener('mousedown', function (e) { if (!wrap.contains(e.target)) close(); });
    sel.addEventListener('change', paintBtn);
    paintBtn();
  }

  onPageReady(function () {
    Array.prototype.forEach.call(document.querySelectorAll('.bid-select select, .sch-pick select'), enhance);
  });
})();

/* ---------- 모든 드롭다운의 펼친 목록 (11/09) ----------
   브라우저(크롬·사파리·파이어폭스·맥/윈도)마다 펼친 목록 모양이 달라서, 모든 select의 목록을 우리 모양(.csel-pop)으로 띄운다.
   select 자체는 그 자리에 그대로 두고 여는 동작만 가로챈다 → 크기·폼 제출·필수 검사는 원래대로.
   문서 전체에서 받으므로 나중에 생긴 select(팝업·행 추가)에도 붙는다. 사업 드롭다운(.csel)은 위쪽 코드가 따로 맡는다.
   빼는 것: 여러 줄 목록(multiple·size), 비활성, data-native 표시, 터치 화면(휴대폰은 기본 선택창이 더 편하다). */
(function () {
  var CHECK = '<svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 7.2 5.8 10 11 4.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var touch = window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
  var pop = null, cur = null; // 열린 목록과 그 select

  function esc(t) { var d = document.createElement('div'); d.textContent = t == null ? '' : t; return d.innerHTML; }
  function usable(el) {
    return el && el.tagName === 'SELECT' && !el.multiple && !(el.size > 1) && !el.disabled
      && !el.classList.contains('csel-native') && !el.hasAttribute('data-native') && !touch;
  }
  function item(o) {
    if (o.hidden) return '';
    var name = o.getAttribute('data-name') || o.textContent.trim(), sub = o.getAttribute('data-sub') || '';
    var on = o.selected, off = o.disabled;
    return '<div class="csel-opt' + (on ? ' on' : '') + (off ? ' off' : '') + '" role="option" aria-selected="' + on + '"' +
      (off ? ' aria-disabled="true"' : '') + ' data-i="' + o.index + '" title="' + esc(o.textContent.trim()) + '">' +
      '<span class="csel-ck">' + (on ? CHECK : '') + '</span><span class="csel-nm">' + (esc(name) || '&nbsp;') + '</span>' +
      (sub ? '<span class="csel-sub">' + esc(sub) + '</span>' : '') + '</div>';
  }
  function build(sel) {
    var html = '';
    Array.prototype.forEach.call(sel.children, function (n) {
      if (n.tagName === 'OPTGROUP') {
        html += '<div class="csel-grp">' + esc(n.label) + '</div>';
        Array.prototype.forEach.call(n.children, function (o) { html += item(o); });
      } else if (n.tagName === 'OPTION') { html += item(n); }
    });
    return html || '<div class="csel-empty">고를 항목이 없습니다</div>';
  }
  function opts() { return pop ? Array.prototype.slice.call(pop.querySelectorAll('.csel-opt:not(.off)')) : []; }
  function setActive(el) {
    opts().forEach(function (x) { x.classList.toggle('act', x === el); });
    if (!el) return;
    if (el.offsetTop < pop.scrollTop) pop.scrollTop = el.offsetTop - 6;
    else if (el.offsetTop + el.offsetHeight > pop.scrollTop + pop.clientHeight) pop.scrollTop = el.offsetTop + el.offsetHeight - pop.clientHeight + 6;
  }
  function place() {
    var r = cur.getBoundingClientRect(), vw = window.innerWidth, vh = window.innerHeight;
    pop.style.minWidth = Math.round(r.width) + 'px';
    pop.style.left = '0px'; pop.style.top = '0px';
    var w = pop.offsetWidth, h = pop.offsetHeight;
    var left = Math.min(r.left, vw - w - 8), top = r.bottom + 6;
    if (top + h > vh - 8 && r.top - 6 - h > 8) top = r.top - 6 - h; // 아래 자리가 모자라면 위로
    pop.style.left = Math.max(8, left) + 'px';
    pop.style.top = Math.max(8, top) + 'px';
  }
  function open(sel) {
    close();
    cur = sel;
    pop = document.createElement('div');
    pop.className = 'csel-pop float';
    pop.setAttribute('role', 'listbox');
    pop.innerHTML = build(sel);
    document.body.appendChild(pop);
    sel.classList.add('csel-open');
    sel.setAttribute('aria-expanded', 'true');
    place();
    setActive(pop.querySelector('.csel-opt.on:not(.off)') || opts()[0]);
    pop.addEventListener('mousedown', function (e) { e.preventDefault(); }); // select 포커스 유지
    pop.addEventListener('click', function (e) {
      var el = e.target.closest('.csel-opt');
      if (el && !el.classList.contains('off')) choose(el);
    });
    pop.addEventListener('mousemove', function (e) {
      var el = e.target.closest('.csel-opt');
      if (el && !el.classList.contains('off') && !el.classList.contains('act')) opts().forEach(function (x) { x.classList.toggle('act', x === el); });
    });
  }
  function close() {
    if (!pop) return;
    pop.remove();
    if (cur) { cur.classList.remove('csel-open'); cur.setAttribute('aria-expanded', 'false'); }
    pop = null; cur = null;
  }
  function choose(el) {
    var sel = cur, i = +el.getAttribute('data-i');
    close();
    sel.focus();
    if (i === sel.selectedIndex) return;
    sel.selectedIndex = i;
    sel.dispatchEvent(new Event('input', { bubbles: true }));
    sel.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // 누르면 기본 목록 대신 우리 목록
  document.addEventListener('mousedown', function (e) {
    if (pop && !pop.contains(e.target) && e.target !== cur) close();
    var sel = e.target;
    if (e.button !== 0 || !usable(sel)) return;
    e.preventDefault();
    sel.focus();
    if (pop && cur === sel) close(); else open(sel);
  }, true);

  // 키보드: 닫혀 있을 때 ↑↓ Enter Space Alt+↓ 로 열고, 열려 있으면 ↑↓ Home End Enter Esc Tab
  document.addEventListener('keydown', function (e) {
    var sel = e.target;
    if (pop && sel === cur) {
      var list = opts(), a = pop.querySelector('.csel-opt.act'), i = list.indexOf(a);
      if (e.key === 'ArrowDown') { e.preventDefault(); setActive(list[Math.min(i + 1, list.length - 1)]); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(list[Math.max(i - 1, 0)]); }
      else if (e.key === 'Home') { e.preventDefault(); setActive(list[0]); }
      else if (e.key === 'End') { e.preventDefault(); setActive(list[list.length - 1]); }
      else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (a) choose(a); else close(); }
      else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); }
      else if (e.key === 'Tab') { close(); }
      return;
    }
    if (!usable(sel)) return;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(sel); }
  }, true);

  // 목록 밖을 굴리거나 창 크기가 바뀌면 닫는다 (목록 안 스크롤은 그대로)
  document.addEventListener('scroll', function (e) { if (pop && !pop.contains(e.target)) close(); }, true);
  window.addEventListener('resize', close);
  window.addEventListener('blur', close);
})();

/* ---------- 줄 끝 "…" 메뉴 (11/09: proposal.js에서 옮김, 어느 화면에서나 쓴다) ---------- */
/* 줄 끝 "…" 메뉴. 표를 감싼 칸(.ts)이 넘치는 부분을 가리므로 화면 기준(fixed)으로 버튼 아래 오른쪽 맞춤에 띄운다 */
function closeRowMenus() {
  document.querySelectorAll('.row-menu .menu-pop').forEach(function (p) {
    p.hidden = true;
    var b = p.parentNode.querySelector('.kebab');
    if (b) b.setAttribute('aria-expanded', 'false');
  });
}

function toggleRowMenu(btn) {
  var pop = btn.parentNode.querySelector('.menu-pop');
  var opening = pop.hidden;
  closeRowMenus();
  if (!opening) return;
  pop.hidden = false;
  var r = btn.getBoundingClientRect(), w = pop.offsetWidth, h = pop.offsetHeight;
  var top = r.bottom + 4;
  if (top + h > window.innerHeight - 8) top = Math.max(8, r.top - h - 4); // 아래가 모자라면 위로
  pop.style.top = top + 'px';
  pop.style.left = Math.max(8, r.right - w) + 'px';
  btn.setAttribute('aria-expanded', 'true');
}

(function () {
  document.addEventListener('click', function (e) {
    var inPop = e.target.closest && e.target.closest('.menu-pop');
    if (inPop) { if (e.target.closest('button')) closeRowMenus(); return; }  // 메뉴 항목을 누르면 닫는다
    if (!(e.target.closest && e.target.closest('.row-menu'))) closeRowMenus();       // 바깥을 누르면 닫는다
  });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeRowMenus(); });
  window.addEventListener('scroll', closeRowMenus, true);
  window.addEventListener('resize', closeRowMenus);
})();

/* ---------- 사번 하이픈 자동 입력 (11/09) ----------
   <input data-empno> : 숫자만 받고 앞 4자리를 치면 바로 "-"를 붙인다 (2024-0001). 붙여넣기 "20240001"도 맞춰 준다.
   지울 때는 "-"를 다시 붙이지 않아 Backspace로 자연스럽게 지워진다. 읽기 전용 칸은 건드리지 않는다 */
(function () {
  function fmt(v, adding) {
    var d = String(v || '').replace(/\D/g, '').slice(0, 8);
    if (d.length > 4) return d.slice(0, 4) + '-' + d.slice(4);
    if (d.length === 4 && adding) return d + '-';
    return d;
  }
  document.addEventListener('input', function (e) {
    var el = e.target;
    if (!el || !el.hasAttribute || !el.hasAttribute('data-empno') || el.readOnly) return;
    var adding = !/^delete/.test(e.inputType || '');
    var next = fmt(el.value, adding);
    if (next !== el.value) {
      el.value = next;
      try { el.setSelectionRange(next.length, next.length); } catch (x) { /* 일부 브라우저 */ }
    }
  });
})();
