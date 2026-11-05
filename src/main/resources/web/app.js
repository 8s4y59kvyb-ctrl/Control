'use strict';

// Интерфейс курсанта. Все проверки ответов — на сервере; здесь только показ состояния,
// таймер (отсчёт от secondsLeft сервера) и отрисовка схемы по командам с сервера.

const $ = id => document.getElementById(id);
const FLOW_SPEED = 70;                       // скорость точек тока, px/с
const SESSION_KEY = 'electroquiz.session';
const WINDOW_KEY = 'electroquiz.window';

let meta = null;                             // темы и уровни с сервера
let state = null;                            // последнее состояние теста
let deadlineAt = 0;                          // момент окончания времени по часам браузера, мс
let tick = null;
let flowPaths = [];
let flowColor = '#2563eb';
let flowStart = 0;
const chartsShown = new Set();               // задания (sessionId:taskNumber), график которых уже показан

async function api(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch (e) { /* пустой ответ */ }
  if (!res.ok) {
    const err = new Error((data && data.error) || 'Ошибка сервера (' + res.status + ').');
    err.status = res.status;
    throw err;
  }
  return data;
}

function show(id) {
  for (const s of ['start', 'quiz', 'result']) $(s).hidden = s !== id;
}

/** Случайный номер окна браузера: переживает перезагрузку, у новой вкладки свой. randomUUID на http недоступен. */
function windowId() {
  let id = sessionStorage.getItem(WINDOW_KEY);
  if (!id) {
    id = Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join('');
    sessionStorage.setItem(WINDOW_KEY, id);
  }
  return id;
}

// ---------- старт ----------
async function init() {
  meta = await api('GET', '/api/themes');
  buildDisciplines();
  const box = $('levels');
  box.innerHTML = '';
  meta.levels.forEach((l, i) => {
    const label = document.createElement('label');
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'level';
    input.value = l.id;
    input.checked = i === 0;
    label.appendChild(input);
    label.appendChild(document.createTextNode(l.title));
    box.appendChild(label);
  });
  box.addEventListener('change', syncStartForm);
  $('disciplines').addEventListener('change', syncStartForm);
  syncStartForm();

  const saved = sessionStorage.getItem(SESSION_KEY);
  if (saved) {
    try {
      const st = await api('GET', '/api/sessions/' + encodeURIComponent(saved));
      if (st.taskNumber) chartsShown.add(st.sessionId + ':' + st.taskNumber);   // после перезагрузки график не повторяем
      render(st);
      return;
    } catch (e) {
      sessionStorage.removeItem(SESSION_KEY);
    }
  }
  show('start');
}

/** Вкладки дисциплин: по нажатию раскрывается список тем (открыта одна вкладка); тема выбирается переключателем. */
function buildDisciplines() {
  const box = $('disciplines');
  box.innerHTML = '';
  for (const d of meta.disciplines) {
    const themes = meta.themes.filter(t => t.discipline === d.id);
    const wrap = document.createElement('div');
    wrap.className = 'disc';
    const head = document.createElement('button');
    head.type = 'button';
    head.className = 'disc-head';
    head.setAttribute('aria-expanded', 'false');
    const count = document.createElement('span');
    count.className = 'disc-count';
    count.textContent = themes.length ? themeCount(themes.length) : 'в разработке';
    head.append(d.title, count);
    const list = document.createElement('div');
    list.className = 'disc-themes';
    list.hidden = true;
    if (!themes.length) {
      const p = document.createElement('div');
      p.className = 'muted disc-empty';
      p.textContent = 'Темы по этой дисциплине пока в разработке.';
      list.appendChild(p);
    }
    for (const t of themes) {
      const label = document.createElement('label');
      label.className = 'theme-opt' + (t.ready ? '' : ' theme-off');
      const input = document.createElement('input');
      input.type = 'radio';
      input.name = 'theme';
      input.value = t.id;
      input.disabled = !t.ready;                // тема «в разработке»: видна, но не выбирается
      label.append(input, t.title);
      list.appendChild(label);
    }
    head.addEventListener('click', () => {
      const open = list.hidden;
      for (const w of box.children) {           // открыта только одна дисциплина
        w.querySelector('.disc-themes').hidden = true;
        w.querySelector('.disc-head').setAttribute('aria-expanded', 'false');
      }
      list.hidden = !open;
      head.setAttribute('aria-expanded', String(open));
      if (open) {                               // тема другой дисциплины больше не выбрана
        for (const r of box.querySelectorAll('input[name=theme]')) if (!list.contains(r)) r.checked = false;
        syncStartForm();
      }
    });
    wrap.append(head, list);
    box.appendChild(wrap);
  }
}

function themeCount(n) {
  const m10 = n % 10, m100 = n % 100;
  const word = m10 === 1 && m100 !== 11 ? 'тема' : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? 'темы' : 'тем';
  return n + ' ' + word;
}

function selectedTheme() {
  const r = document.querySelector('input[name=theme]:checked');
  return r ? meta.themes.find(t => t.id === r.value) : null;
}

function selectedLevel() {
  const r = document.querySelector('input[name=level]:checked');
  return r ? r.value : null;
}

/** Описание выбранной темы, выбор сложности (если он есть) и правила выбранного уровня. */
function syncStartForm() {
  const theme = selectedTheme();
  for (const l of document.querySelectorAll('.theme-opt')) l.classList.toggle('on', l.firstChild.checked);
  $('theme-info').textContent = theme ? theme.description : '';
  $('level-box').hidden = !theme || !theme.hasLevels;
  for (const l of $('levels').children) l.classList.toggle('on', l.firstChild.checked);
  const lvl = meta.levels.find(l => l.id === selectedLevel());
  $('start-rules').textContent = lvl ? lvl.rules : '';
}

// группа — только цифры и тире: лишнее не даём ввести; фамилия — русские буквы: подсказываем про раскладку
$('group').addEventListener('input', () => {
  const g = $('group');
  const clean = g.value.replace(/[^0-9\-‐‑‒–—−]/g, '');
  if (clean !== g.value) g.value = clean;
});
$('surname').addEventListener('input', () => {
  $('start-error').textContent = /[^А-ЯЁа-яё \-‐‑‒–—−]/.test($('surname').value)
    ? 'Фамилию введите русскими буквами (проверьте раскладку клавиатуры).' : '';
});

$('start-form').addEventListener('submit', async ev => {
  ev.preventDefault();
  $('start-error').textContent = '';
  const theme = selectedTheme();
  if (!theme) {
    $('start-error').textContent = 'Выберите дисциплину и тему.';
    return;
  }
  $('start-btn').disabled = true;
  try {
    const body = {
      group: $('group').value,
      surname: $('surname').value,
      theme: theme.id,
      level: theme.hasLevels ? selectedLevel() : null,
    };
    await api('POST', '/api/login', { group: body.group, surname: body.surname });
    const st = await api('POST', '/api/sessions', { ...body, windowId: windowId() });
    sessionStorage.setItem(SESSION_KEY, st.sessionId);
    render(st);
  } catch (e) {
    $('start-error').textContent = e.message;
  } finally {
    $('start-btn').disabled = false;
  }
});

// ---------- показ состояния ----------
function render(st) {
  state = st;
  if (st.status === 'finished') {
    renderResult(st);
    return;
  }
  show('quiz');

  $('who').textContent = 'Группа ' + st.group + ' · ' + st.surname;
  $('task-title').textContent = 'Задание №' + st.taskNumber + ' из ' + st.tasksTotal
    + (st.hasLevels ? ' · ' + st.level : '');
  $('theme-label').textContent = 'Тема: ' + st.theme;
  $('task-text').textContent = st.task.text;
  $('rules').textContent = st.rules;

  const done = st.status === 'task_done';
  buildFields(st, done);
  if (done && st.task.chart && !chartsShown.has(st.sessionId + ':' + st.taskNumber)) {
    showChart(st);
    return;
  }
  $('check-btn').hidden = done;
  $('calc-btn').hidden = !!st.task.options;     // в задании на выбор считать нечего
  $('next-btn').hidden = !done;
  $('next-btn').textContent = st.lastTask ? 'Завершить тестирование' : 'Следующее задание';

  setFeedback(st.message || '', st.messageKind);
  const g = $('grade');
  const known = done && st.taskGrade !== undefined;   // оценку за задание сервер может не сообщать
  const good = st.taskGrade >= 3;
  // оценка за тест целиком (модуляция): за задание — только «верно / неверно» и счёт верных ответов
  $('grade-title').textContent = st.wholeTest ? 'Результат задания' : 'Оценка за задание';
  g.textContent = !known ? '—' : st.wholeTest ? (good ? 'верно' : 'неверно') : st.taskGrade;
  g.className = 'grade-value' + (st.wholeTest ? ' grade-word' : '') + (known ? (good ? ' grade-good' : ' grade-bad') : '');
  $('solved').hidden = st.solved === undefined;
  if (st.solved !== undefined) $('solved').textContent = 'Верных ответов: ' + st.solved + ' из ' + st.tasksTotal;

  drawDots(st, done);
  drawScheme(st.task.scheme);

  deadlineAt = Date.now() + st.secondsLeft * 1000;
  startTimer();
}

/** После задания (тема 4 — только верного, тема 5 — любого): вместо схемы на chartSec секунд — график, затем оценка. */
function showChart(st) {
  const key = st.sessionId + ':' + st.taskNumber;
  chartsShown.add(key);
  clearInterval(tick);
  updateTimer();                               // время стоит: задание завершено
  $('check-btn').hidden = true;
  $('next-btn').hidden = true;
  setFeedback((st.taskGrade >= 3 ? 'Верно! ' : '') + 'График зависимости от частоты. Оценка появится через несколько секунд…', null);
  const g = $('grade');
  g.textContent = '—';
  g.className = 'grade-value';
  drawDots(st, false);
  drawScheme(st.task.chart);
  setTimeout(() => {
    if (state && state.sessionId + ':' + state.taskNumber === key && state.status !== 'finished') render(state);
  }, (st.chartSec || 5) * 1000);
}

/** Поля ответа пересоздаются только при новом задании, иначе при неверной попытке потерялся бы ввод. */
function buildFields(st, done) {
  const key = st.sessionId + ':' + st.taskNumber;
  const box = $('fields');
  if (box.dataset.key !== key) {
    box.dataset.key = key;
    box.innerHTML = '';
    box.className = 'fields' + (st.task.options ? ' choices' : '');
    if (st.task.options) buildChoices(box, st.task.options);
    else for (const f of st.task.fields) {
      const wrap = document.createElement('label');
      wrap.className = 'answer';
      const input = document.createElement('input');
      input.type = 'text';
      input.inputMode = 'decimal';
      input.maxLength = 30;
      input.setAttribute('aria-label', f.symbol);
      wrap.append(f.symbol + ' =', input, f.unit);
      box.appendChild(wrap);
    }
    if (!done && !st.task.options) box.querySelector('input').focus();
  }
  for (const i of box.querySelectorAll('input')) i.disabled = done;
  if (st.task.options) markChoices(box, done ? st.task.correct : 0);
}

/** Задание на выбор (модуляция): варианты-картинки с номерами, выбор щелчком по картинке. */
function buildChoices(box, options) {
  options.forEach((o, i) => {
    const label = document.createElement('label');
    label.className = 'choice';
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'choice';
    input.value = String(i + 1);
    input.setAttribute('aria-label', 'Вариант ' + (i + 1));
    input.addEventListener('change', () => markChoices(box, 0));
    const num = document.createElement('span');
    num.className = 'choice-num';
    num.textContent = i + 1;
    const canvas = document.createElement('canvas');
    label.append(input, num, canvas);
    box.appendChild(label);
    drawOps(canvas, o);
  });
}

/** Подсветка: выбранный вариант; после задания (correct > 0) — верный зелёным, неверно выбранный красным. */
function markChoices(box, correct) {
  for (const label of box.querySelectorAll('.choice')) {
    const input = label.querySelector('input');
    const right = correct > 0 && Number(input.value) === correct;
    label.classList.toggle('on', input.checked && !correct);
    label.classList.toggle('choice-ok', right);
    label.classList.toggle('choice-bad', correct > 0 && input.checked && !right);
  }
}

function setFeedback(text, kind) {
  const f = $('feedback');
  f.textContent = text;
  f.className = 'feedback' + (kind ? ' fb-' + kind : '');
}

function drawDots(st, done) {
  const box = $('dots');
  box.innerHTML = '';
  for (let i = 0; i < st.attemptsMax; i++) {
    const d = document.createElement('span');
    d.className = 'dot';
    if (i < st.attempt) {
      const good = done && st.taskGrade >= 3 && i === st.attempt - 1;
      d.classList.add(good ? 'dot-ok' : 'dot-used');
    }
    box.appendChild(d);
  }
  $('attempts').textContent = 'Использовано ' + st.attempt + ' из ' + st.attemptsMax;
}

// ---------- таймер ----------
function startTimer() {
  clearInterval(tick);
  updateTimer();
  if (state.status === 'task') tick = setInterval(updateTimer, 250);
}

let lastPoll = 0;
function updateTimer() {
  const left = Math.max(0, Math.ceil((deadlineAt - Date.now()) / 1000));
  const done = state.status !== 'task';
  const shown = done ? 0 : left;
  $('timer').textContent = String(Math.floor(shown / 60)).padStart(2, '0') + ':' + String(shown % 60).padStart(2, '0');
  $('timer-fill').style.width = (done ? 0 : 100 * left / state.timeLimitSec) + '%';
  $('timer').parentElement.classList.toggle('timer-warn', !done && left <= 30);
  // время вышло: решение принимает сервер (у него небольшой запас на сеть) — спрашиваем раз в секунду
  if (!done && left === 0 && Date.now() - lastPoll > 1000) {
    lastPoll = Date.now();
    api('GET', '/api/sessions/' + encodeURIComponent(state.sessionId)).then(render).catch(handleError);
  }
}

// ---------- ответы ----------
$('answer-form').addEventListener('submit', async ev => {
  ev.preventDefault();
  if (!state || state.status !== 'task') return;
  const inputs = [...$('fields').querySelectorAll('input')];
  const choice = !!state.task.options;         // задание на выбор: одно значение — номер варианта
  const picked = $('fields').querySelector('input:checked');
  $('check-btn').disabled = true;
  try {
    const st = await api('POST', '/api/sessions/' + encodeURIComponent(state.sessionId) + '/answer',
      { values: choice ? [picked ? picked.value : ''] : inputs.map(i => i.value) });
    render(st);
    if (st.status === 'task' && !choice) inputs[0].select();
  } catch (e) {
    handleError(e);
  } finally {
    $('check-btn').disabled = false;
  }
});

$('next-btn').addEventListener('click', async () => {
  $('next-btn').disabled = true;
  try {
    render(await api('POST', '/api/sessions/' + encodeURIComponent(state.sessionId) + '/next'));
  } catch (e) {
    handleError(e);
  } finally {
    $('next-btn').disabled = false;
  }
});

function handleError(e) {
  if (e.status === 404) {                      // тест устарел или сервер перезапущен
    sessionStorage.removeItem(SESSION_KEY);
    clearInterval(tick);
    $('start-error').textContent = e.message;
    show('start');
  } else {
    setFeedback(e.message, 'warn');
  }
}

// ---------- итог ----------
function renderResult(st) {
  clearInterval(tick);
  flowPaths = [];
  sessionStorage.removeItem(SESSION_KEY);
  const r = st.result;
  let info = 'Группа: ' + st.group + '\nКурсант: ' + st.surname + '\nТема: ' + st.theme + '\n';
  for (const t of r.tasks) {
    info += '\nЗадание ' + t.number + ' (' + t.kind + '): '
      + (st.wholeTest ? (t.grade >= 3 ? 'верно' : 'неверно') : 'оценка ' + t.grade);
  }
  if (st.solved !== undefined) info += '\n\nВерных ответов: ' + st.solved + ' из ' + r.tasks.length;
  $('result-info').textContent = info;
  const g = $('result-grade');
  g.textContent = r.finalGrade;
  g.className = 'grade-value ' + (r.passed ? 'grade-good' : 'grade-bad');
  show('result');
}

$('again-btn').addEventListener('click', () => {
  $('surname').value = '';                      // тест проходит следующий курсант; группа остаётся
  $('start-error').textContent = '';
  show('start');
  $('surname').focus();
});

// ---------- схема ----------
function setupCanvas(canvas, w, h) {
  const k = window.devicePixelRatio || 1;
  canvas.width = Math.round(w * k);
  canvas.height = Math.round(h * k);
  const ctx = canvas.getContext('2d');
  ctx.setTransform(k, 0, 0, k, 0, 0);
  ctx.clearRect(0, 0, w, h);
  return ctx;
}

/** Схема или график на главном холсте и пути тока для анимации. */
function drawScheme(s) {
  drawOps($('scheme'), s);
  setupCanvas($('flow'), s.width, s.height);
  flowPaths = s.flow.map(p => {
    const cum = [0];
    for (let i = 1; i < p.points.length; i++) {
      cum.push(cum[i - 1] + Math.hypot(p.points[i][0] - p.points[i - 1][0], p.points[i][1] - p.points[i - 1][1]));
    }
    return { ...p, cum };
  });
  $('flow-on').parentElement.hidden = !flowPaths.length;   // в схеме с конденсаторами тока нет
  flowColor = s.flowColor;
  flowStart = 0;
}

/** Выполняет команды рисования с сервера (см. ServerSchemeJson) на холсте canvas. */
function drawOps(canvas, s) {
  const ctx = setupCanvas(canvas, s.width, s.height);
  ctx.strokeStyle = ctx.fillStyle = s.wire;
  ctx.lineWidth = s.lineWidth;
  ctx.font = 'bold 15px "Segoe UI", system-ui, sans-serif';
  for (const [op, ...a] of s.ops) {
    switch (op) {
      case 'stroke': ctx.strokeStyle = a[0]; break;
      case 'fill': ctx.fillStyle = a[0]; break;
      case 'lineWidth': ctx.lineWidth = a[0]; break;
      case 'font': ctx.font = 'bold ' + a[0] + 'px "Segoe UI", system-ui, sans-serif'; break;
      case 'line': ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(a[2], a[3]); ctx.stroke(); break;
      case 'fillOval': case 'strokeOval':
        ctx.beginPath();
        ctx.ellipse(a[0] + a[2] / 2, a[1] + a[3] / 2, a[2] / 2, a[3] / 2, 0, 0, 2 * Math.PI);
        op === 'fillOval' ? ctx.fill() : ctx.stroke();
        break;
      case 'fillRect': ctx.fillRect(a[0], a[1], a[2], a[3]); break;
      case 'strokeRect': ctx.strokeRect(a[0], a[1], a[2], a[3]); break;
      case 'polygon':
        ctx.beginPath();
        a[0].forEach((p, i) => i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1]));
        ctx.closePath();
        ctx.fill();
        break;
      case 'polyline':
        ctx.beginPath();
        a[0].forEach((p, i) => i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1]));
        ctx.stroke();
        break;
      case 'text': ctx.fillText(a[0], a[1], a[2]); break;
      case 'save': ctx.save(); break;
      case 'restore': ctx.restore(); break;
      case 'translate': ctx.translate(a[0], a[1]); break;
      case 'rotate': ctx.rotate(a[0] * Math.PI / 180); break;
    }
  }
}

function pointAt(p, s) {
  let i = 1;
  while (i < p.cum.length - 1 && p.cum[i] < s) i++;
  const seg = p.cum[i] - p.cum[i - 1];
  const t = seg === 0 ? 0 : (s - p.cum[i - 1]) / seg;
  const a = p.points[i - 1], b = p.points[i];
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
}

/** Анимация тока: точки бегут по путям, их густота пропорциональна току ветви. */
function animate(now) {
  const canvas = $('flow');
  const ctx = canvas.getContext('2d');
  const k = window.devicePixelRatio || 1;
  ctx.setTransform(k, 0, 0, k, 0, 0);
  ctx.clearRect(0, 0, canvas.width / k, canvas.height / k);
  if ($('flow-on').checked && flowPaths.length && !$('quiz').hidden) {
    if (!flowStart) flowStart = now;
    const shift = (now - flowStart) / 1000 * FLOW_SPEED;
    ctx.fillStyle = flowColor;
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1;
    for (const p of flowPaths) {
      for (let i = 0; i < p.count; i++) {
        const [x, y] = pointAt(p, (p.phase + i * p.spacing + shift) % p.length);
        ctx.beginPath();
        ctx.arc(x, y, 3.5, 0, 2 * Math.PI);
        ctx.fill();
        ctx.stroke();
      }
    }
  }
  requestAnimationFrame(animate);
}
requestAnimationFrame(animate);

init().catch(e => {
  show('start');
  $('start-error').textContent = 'Нет связи с сервером: ' + e.message;
});
