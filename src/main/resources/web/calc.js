'use strict';
/*
 * Встроенный калькулятор страницы курсанта: работает в любом браузере и системе (в том числе Windows 7,
 * где нет калькулятора по ссылке calculator:). Выражение считается своим разбором, без eval.
 * «Вставить в ответ» — в последнее поле ответа, где стоял курсор (иначе в первое пустое).
 */
(function () {
  const $ = id => document.getElementById(id);

  // кнопки: [надпись, что добавить в выражение или действие]
  const KEYS = [
    ['C', 'clear'], ['⌫', 'back'], ['(', '('], [')', ')'], ['÷', '÷'],
    ['7', '7'], ['8', '8'], ['9', '9'], ['×', '×'], ['√', '√('],
    ['4', '4'], ['5', '5'], ['6', '6'], ['−', '−'], ['x²', '²'],
    ['1', '1'], ['2', '2'], ['3', '3'], ['+', '+'], ['1/x', 'inv'],
    ['0', '0'], [',', ','], ['π', 'π'], ['EXP', 'E'], ['=', 'eq'],
  ];
  const TITLES = { 'EXP': 'Степень десяти: 4,7 EXP −6 = 4,7·10⁻⁶', '1/x': 'Обратное значение', '⌫': 'Стереть знак' };

  let expr = '';          // выражение в том виде, как на экране
  let done = false;       // только что нажато «=»: новая цифра начинает новое выражение
  let lastInput = null;   // последнее поле ответа с курсором

  // ---------- разбор выражения ----------
  function tokenize(s) {
    const out = [];
    let i = 0;
    while (i < s.length) {
      const m = /^(\d+[.,]?\d*|[.,]\d+)(E[−-]?\d+)?/.exec(s.slice(i));
      if (m) {
        out.push({ num: parseFloat(m[0].replace(',', '.').replace('−', '-')) });
        i += m[0].length;
      } else if ('+−×÷()√²π-*/'.includes(s[i])) {
        out.push({ op: { '-': '−', '*': '×', '/': '÷' }[s[i]] || s[i] });
        i++;
      } else if (s[i] === ' ') {
        i++;
      } else {
        throw new Error('символ');
      }
    }
    return out;
  }

  function evaluate(s) {
    const t = tokenize(s);
    let p = 0;
    const peek = () => t[p] && t[p].op;
    const startsPrimary = () => t[p] && (t[p].num !== undefined || ['(', '√', 'π'].includes(t[p].op));

    function expression() {
      let v = term();
      while (peek() === '+' || peek() === '−') v = t[p++].op === '+' ? v + term() : v - term();
      return v;
    }
    function term() {
      let v = unary();
      for (;;) {
        if (peek() === '×') { p++; v *= unary(); }
        else if (peek() === '÷') { p++; v /= unary(); }
        else if (startsPrimary()) v *= unary();          // 2π, 3(4+5)
        else return v;
      }
    }
    function unary() {
      if (peek() === '−') { p++; return -unary(); }
      if (peek() === '+') { p++; return unary(); }
      return postfix();
    }
    function postfix() {
      let v = primary();
      while (peek() === '²') { p++; v *= v; }
      return v;
    }
    function primary() {
      const tok = t[p++];
      if (!tok) throw new Error('конец');
      if (tok.num !== undefined) return tok.num;
      if (tok.op === 'π') return Math.PI;
      if (tok.op === '√') return Math.sqrt(postfix());
      if (tok.op === '(') {
        const v = expression();
        if (peek() === ')') p++;                         // незакрытая скобка закрывается в конце
        return v;
      }
      throw new Error('знак');
    }

    if (!t.length) return 0;
    const v = expression();
    if (p < t.length || !isFinite(v)) throw new Error('лишнее');
    return v;
  }

  /** Число для экрана и поля ответа: 10 значащих цифр, запятая, степень как E (1,5E-6). */
  function format(v) {
    if (Object.is(v, -0)) v = 0;
    let s = String(Number(v.toPrecision(10)));
    return s.replace('.', ',').replace('e+', 'E').replace('e', 'E');
  }

  // ---------- экран ----------
  function show(result) {
    $('calc-expr').textContent = expr || ' ';
    if (result !== undefined) { $('calc-out').textContent = result; return; }
    $('calc-out').classList.remove('calc-err');
    try {
      $('calc-out').textContent = expr ? format(evaluate(expr)) : '0';
    } catch (e) {
      // выражение ещё не дописано — оставляем прежний итог
    }
  }

  function press(key) {
    if (key === 'clear') { expr = ''; done = false; show('0'); return; }
    if (key === 'back') { expr = expr.slice(0, -1); done = false; show(); return; }
    if (key === 'eq' || key === 'inv') {
      try {
        const v = evaluate(key === 'inv' ? '1÷(' + expr + ')' : expr);
        expr = format(v);
        done = true;
        $('calc-out').classList.remove('calc-err');
        show(expr);
      } catch (e) {
        $('calc-out').classList.add('calc-err');
        show('Ошибка');
      }
      return;
    }
    // после «=» цифра начинает заново, знак действия продолжает с результатом
    if (done && /^[\d,(√π]/.test(key)) expr = '';
    done = false;
    expr += key;
    show();
  }

  function insert() {
    const fields = [...document.querySelectorAll('#fields input')];
    let target = lastInput && lastInput.isConnected && !lastInput.disabled ? lastInput : null;
    if (!target) target = fields.find(i => !i.disabled && !i.value) || fields.find(i => !i.disabled);
    const text = $('calc-out').textContent;
    if (!target || !/^-?[\d,]+(E-?\d+)?$/.test(text)) return;
    target.value = text;
    target.focus();
  }

  // ---------- кнопки и клавиатура ----------
  const keys = $('calc-keys');
  for (const [label, key] of KEYS) {
    const b = document.createElement('button');
    b.type = 'button';
    b.textContent = label;
    b.className = 'calc-key' + (key === 'eq' ? ' calc-eq' : /^\d$|^,$/.test(key) ? ' calc-digit' : '');
    if (TITLES[label]) b.title = TITLES[label];
    b.addEventListener('click', () => press(key));
    keys.appendChild(b);
  }

  // клавиши ловим, только когда фокус внутри калькулятора: ввод в поля ответа не трогаем
  $('calc').addEventListener('keydown', ev => {
    const k = ev.key;
    const map = { '-': '−', '*': '×', '/': '÷', '.': ',', 'e': 'E', 'E': 'E', 'p': 'π', '^': '²' };
    if (/^[\d+(),]$/.test(k)) press(k);
    else if (map[k]) press(map[k]);
    else if (k === 'Enter' || k === '=') press('eq');
    else if (k === 'Backspace') press('back');
    else if (k === 'Delete') press('clear');
    else if (k === 'Escape') close();
    else return;
    ev.preventDefault();
  });

  document.addEventListener('focusin', ev => {
    if (ev.target.closest && ev.target.closest('#fields')) lastInput = ev.target;
  });

  function open() {
    $('calc').hidden = false;
    $('calc').focus();
  }
  function close() {
    $('calc').hidden = true;
    if (lastInput && lastInput.isConnected && !lastInput.disabled) lastInput.focus();
  }

  $('calc-btn').addEventListener('click', () => ($('calc').hidden ? open() : close()));
  $('calc-close').addEventListener('click', close);
  $('calc-insert').addEventListener('click', insert);
  show('0');
})();
