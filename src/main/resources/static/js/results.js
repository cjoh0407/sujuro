/* 평가·결과 입력(results/index.html): 평가표 입력 칸 늘리기·줄이기
 * 화면 안에 있던 스크립트를 옮긴 파일이다. 화면에서는 같은 자리에서 <script th:src> 로 불러온다. */
// 평가표 입력 칸: 위원 수를 바꾸면 위원 칸을 늘리거나 줄이고, [항목 추가] 는 빈 줄을 붙인다.
// 이름은 서버가 묶는 모양(items[i].nm, items[i].max, items[i].scores[j])과 같아야 한다.
(function () {
  var form = document.getElementById('evl-form');
  if (!form) {
    return;
  }
  var cnt = document.getElementById('evl-cnt');
  var body = document.getElementById('evl-rows');
  var head = form.querySelector('thead tr');

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

  function resize() {
    var n = parseInt(cnt.value, 10);
    if (!(n >= 1 && n <= 20)) {
      return;
    }
    var ths = head.querySelectorAll('th.cm');
    for (var k = ths.length; k < n; k++) {
      var th = document.createElement('th');
      th.className = 'cm';
      th.textContent = '위원' + (k + 1);
      head.appendChild(th);
    }
    for (var m = ths.length - 1; m >= n; m--) {
      head.removeChild(ths[m]);
    }
    body.querySelectorAll('tr').forEach(function (tr, i) {
      var tds = tr.querySelectorAll('td.cm');
      for (var j = tds.length; j < n; j++) {
        tr.appendChild(scoreCell(i, j));
      }
      for (var x = tds.length - 1; x >= n; x--) {
        tr.removeChild(tds[x]);
      }
    });
  }

  cnt.addEventListener('input', resize);
  document.getElementById('evl-add').addEventListener('click', function () {
    var i = body.querySelectorAll('tr').length;
    var tr = document.createElement('tr');
    tr.innerHTML = '<td><input class="nm" maxlength="100"></td>'
        + '<td><input class="num" type="number" min="0" max="100" step="any"></td>';
    tr.querySelector('.nm').name = 'items[' + i + '].nm';
    tr.querySelector('.num').name = 'items[' + i + '].max';
    var n = head.querySelectorAll('th.cm').length;
    for (var j = 0; j < n; j++) {
      tr.appendChild(scoreCell(i, j));
    }
    body.appendChild(tr);
    tr.querySelector('.nm').focus();
  });
})();
