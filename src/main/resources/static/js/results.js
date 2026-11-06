/* 평가·결과 입력(results/index.html): 평가표 입력 칸의 [−][+]
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 <script th:src> 로 불러온다. */
// 평가표 입력 칸의 작은 [−][+]. 위원은 마지막 위원 오른쪽, 항목은 마지막 항목 왼쪽에 있고, [−] 는 항상 마지막 것을 지운다.
// 값이 들어 있으면 한 번 묻는다. 저장 전이라 페이지를 다시 열면 되돌아간다.
// 이름은 서버가 묶는 모양(items[i].nm, items[i].max, items[i].scores[j])과 같아야 한다.
(function () {
  var form = document.getElementById('evl-form');
  if (!form) {
    return;
  }
  var MAX_CMSNR = 20;
  var cnt = document.getElementById('evl-cnt');
  var body = document.getElementById('evl-rows');
  var head = form.querySelector('thead tr');
  var headEnd = head.querySelector('.ctl-end');
  var box = form.querySelector('.evl-scroll');
  var cmMinus = document.getElementById('evl-cm-minus');
  var cmPlus = document.getElementById('evl-cm-plus');
  var rowCtl = document.getElementById('evl-row-ctl');
  var rowMinus = document.getElementById('evl-row-minus');
  var rowPlus = document.getElementById('evl-row-plus');

  function cmsnrCount() {
    return head.querySelectorAll('th.cm').length;
  }

  function rows() {
    return body.querySelectorAll('tr');
  }

  function hasValue(inputs) {
    return Array.prototype.some.call(inputs, function (input) {
      return input.value.trim() !== '';
    });
  }

  // 숨은 칸과 버튼 잠금을 지금 칸 수에 맞춘다
  function refresh() {
    var n = cmsnrCount();
    cnt.value = n;
    cmMinus.disabled = n <= 1;
    cmPlus.disabled = n >= MAX_CMSNR;
    rowMinus.disabled = rows().length <= 1;
  }

  function scoreCell(i, j) {
    var td = document.createElement('td');
    td.className = 'cm';
    var input = document.createElement('input');
    input.className = 'num';
    input.type = 'number';
    input.min = '0';
    input.step = 'any';
    input.name = 'items[' + i + '].scores[' + j + ']';
    td.appendChild(input);
    return td;
  }

  cmPlus.addEventListener('click', function () {
    var n = cmsnrCount();
    var th = document.createElement('th');
    th.className = 'cm';
    th.textContent = '위원' + (n + 1);
    head.insertBefore(th, headEnd);
    rows().forEach(function (tr, i) {
      tr.insertBefore(scoreCell(i, n), tr.lastElementChild);
    });
    refresh();
    box.scrollLeft = box.scrollWidth; // 새 위원 칸과 버튼이 보이게 한다
  });

  cmMinus.addEventListener('click', function () {
    var n = cmsnrCount();
    var inputs = body.querySelectorAll('td.cm:nth-last-child(2) input');
    if (hasValue(inputs) && !confirm('위원' + n + '의 점수를 지울까요?')) {
      return;
    }
    head.removeChild(head.querySelectorAll('th.cm')[n - 1]);
    rows().forEach(function (tr) {
      tr.removeChild(tr.querySelectorAll('td.cm')[n - 1]);
    });
    refresh();
  });

  rowPlus.addEventListener('click', function () {
    var i = rows().length;
    var tr = document.createElement('tr');
    tr.innerHTML = '<td class="ctl"></td><td><input class="nm" maxlength="100"></td>'
        + '<td><input class="num" type="number" min="0" max="100" step="any"></td>';
    tr.querySelector('.nm').name = 'items[' + i + '].nm';
    tr.querySelector('.num').name = 'items[' + i + '].max';
    for (var j = 0, n = cmsnrCount(); j < n; j++) {
      tr.appendChild(scoreCell(i, j));
    }
    var end = document.createElement('td');
    end.className = 'ctl-end';
    tr.appendChild(end);
    body.appendChild(tr);
    tr.firstElementChild.appendChild(rowCtl);
    refresh();
    tr.querySelector('.nm').focus();
  });

  rowMinus.addEventListener('click', function () {
    var list = rows();
    var last = list[list.length - 1];
    if (hasValue(last.querySelectorAll('input'))) {
      var nm = last.querySelector('.nm').value.trim();
      if (!confirm((nm ? '평가 항목 "' + nm + '"' : '마지막 평가 항목') + '을 지울까요?')) {
        return;
      }
    }
    list[list.length - 2].firstElementChild.appendChild(rowCtl);
    body.removeChild(last);
    refresh();
    (rowMinus.disabled ? rowPlus : rowMinus).focus(); // 옮기면서 풀린 포커스를 되돌린다
  });
})();
