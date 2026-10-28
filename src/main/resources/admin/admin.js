'use strict';

// Страница преподавателя: вход по паролю, таблица результатов с фильтрами, выгрузка CSV
// и настройки тем (время на задание, число заданий, пауза перед пересдачей).
// Пароль вводится в форму страницы (не в окно браузера) и подставляется в каждый запрос к /api/admin.
// Хранится только до закрытия вкладки (sessionStorage).

const $ = id => document.getElementById(id);
const KEY = 'electroquiz.admin';

let password = '';
try { password = sessionStorage.getItem(KEY) || ''; } catch (e) { /* хранилище недоступно — спросим пароль */ }

class AuthError extends Error {}

function query() {
  const p = new URLSearchParams();
  for (const [key, id] of [['group', 'f-group'], ['theme', 'f-theme'], ['from', 'f-from'], ['to', 'f-to']]) {
    if ($(id).value) p.set(key, $(id).value);
  }
  return p;
}

// btoa понимает только латиницу, поэтому пароль сначала переводится в байты UTF-8
function authHeader() {
  const bytes = new TextEncoder().encode(':' + password);
  return 'Basic ' + btoa(String.fromCharCode(...bytes));
}

// body — объект, уходит как JSON
async function request(url, method = 'GET', body) {
  const headers = url.startsWith('/api/admin/') ? { Authorization: authHeader() } : {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.ok) return res;
  const data = await res.json().catch(() => null);
  const message = (data && data.error) || 'Ошибка сервера (' + res.status + ').';
  if (res.status === 401 || res.status === 429) throw new AuthError(message);
  throw new Error(message);
}

const get = async url => (await request(url)).json();

function showLogin(message) {
  password = '';
  try { sessionStorage.removeItem(KEY); } catch (e) { /* ничего */ }
  $('journal').hidden = true;
  $('login').hidden = false;
  $('login-error').textContent = message || '';
  $('password').value = '';
  $('password').focus();
}

function fail(e, target = 'error') {
  if (e instanceof AuthError) {
    showLogin(e.message);
  } else {
    $(target).textContent = e.message;
    $(target).className = 'feedback fb-bad';
  }
}

async function load() {
  $('error').textContent = '';
  try {
    const data = await get('/api/admin/results?' + query());
    const body = $('rows');
    body.innerHTML = '';
    for (const r of data.items) {
      const tr = document.createElement('tr');
      for (const v of [r.id, r.date, r.time, r.group, r.surname, r.theme, r.level, r.grade]) {
        const td = document.createElement('td');
        td.textContent = v;
        tr.appendChild(td);
      }
      body.appendChild(tr);
    }
    $('summary').textContent = 'Записей: ' + data.total
      + (data.total > data.items.length ? ' (показаны первые ' + data.items.length + ', в CSV попадут все)' : '');
  } catch (e) {
    fail(e);
  }
}

// CSV скачивается через fetch: обычная ссылка не может передать пароль в заголовке
async function downloadCsv() {
  $('error').textContent = '';
  try {
    const p = query();
    const blob = await (await request('/api/admin/results.csv' + (p.toString() ? '?' + p : ''))).blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'Результаты ElectroQuiz.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 10000);
  } catch (e) {
    fail(e);
  }
}

// ---------- вкладки ----------
function showTab(name) {
  for (const b of document.querySelectorAll('.tab')) b.classList.toggle('on', b.dataset.tab === name);
  $('tab-results').hidden = name !== 'results';
  $('tab-settings').hidden = name !== 'settings';
  if (name === 'settings') loadSettings();       // каждый раз заново: настройки мог сменить другой преподаватель
}

// ---------- настройки ----------
function numberInput(value, min, max, label) {
  const i = document.createElement('input');
  Object.assign(i, { type: 'number', className: 'num', min, max, step: 1, value, required: true });
  i.setAttribute('aria-label', label);
  return i;
}

function cell(tr, content, colSpan) {
  const td = document.createElement('td');
  if (typeof content === 'string') td.textContent = content; else td.appendChild(content);
  if (colSpan) td.colSpan = colSpan;
  tr.appendChild(td);
  return td;
}

function renderSettings(s) {
  const L = s.limits;
  const body = $('settings-rows');
  body.innerHTML = '';
  for (const t of s.themes) {
    const tr = document.createElement('tr');
    tr.dataset.id = t.id;
    cell(tr, t.title).className = 'theme-name';
    if (t.hasLevels) {
      cell(tr, numberInput(t.easyMin, L.minMinutes, L.maxMinutes, t.title + ': лёгкий уровень, минут')).dataset.f = 'easyMin';
      cell(tr, numberInput(t.hardMin, L.minMinutes, L.maxMinutes, t.title + ': сложный уровень, минут')).dataset.f = 'hardMin';
    } else {                                   // без выбора сложности — одно время
      const td = cell(tr, numberInput(t.hardMin, L.minMinutes, L.maxMinutes, t.title + ': минут'), 2);
      td.dataset.f = 'hardMin';
      td.append(' без выбора сложности');
    }
    cell(tr, numberInput(t.tasks, L.minTasks, L.maxTasks, t.title + ': заданий')).dataset.f = 'tasks';
    const def = t.hasLevels && t.defaultEasyMin !== t.defaultHardMin
      ? t.defaultEasyMin + ' / ' + t.defaultHardMin : String(t.defaultHardMin);
    cell(tr, def + ' мин, заданий: ' + t.defaultTasks).className = 'muted';
    body.appendChild(tr);
  }
  $('retake').value = s.retakeMin;
  $('retake').max = L.maxRetakeMin;
  $('retake-default').textContent = 'по умолчанию ' + s.defaultRetakeMin;
}

async function loadSettings() {
  $('settings-msg').textContent = '';
  try {
    renderSettings(await get('/api/admin/settings'));
  } catch (e) {
    fail(e, 'settings-msg');
  }
}

function collectSettings() {
  const themes = [];
  for (const tr of $('settings-rows').children) {
    const t = { id: tr.dataset.id };
    for (const td of tr.querySelectorAll('td[data-f]')) t[td.dataset.f] = td.querySelector('input').value;
    if (t.easyMin === undefined) t.easyMin = t.hardMin;
    themes.push(t);
  }
  return { themes, retakeMin: $('retake').value };
}

function settingsDone(s, text) {
  renderSettings(s);
  $('settings-msg').textContent = text;
  $('settings-msg').className = 'feedback fb-ok';
}

async function saveSettings(ev) {
  ev.preventDefault();
  if (!$('settings-form').reportValidity()) return;     // браузер покажет, какое поле вне пределов
  try {
    const s = await (await request('/api/admin/settings', 'PUT', collectSettings())).json();
    settingsDone(s, 'Сохранено. Новые значения действуют на тесты, начатые с этого момента.');
  } catch (e) {
    fail(e, 'settings-msg');
  }
}

async function resetSettings() {
  if (!confirm('Вернуть время, число заданий и паузу перед пересдачей к значениям по умолчанию?')) return;
  try {
    const s = await (await request('/api/admin/settings/reset', 'POST')).json();
    settingsDone(s, 'Восстановлены значения по умолчанию.');
  } catch (e) {
    fail(e, 'settings-msg');
  }
}

// Первый запрос с паролем заодно проверяет его: при неверном снова показывается форма входа
async function enter() {
  try {
    const [groups, meta] = await Promise.all([get('/api/admin/groups'), get('/api/themes')]);
    $('f-group').length = 1;
    $('f-theme').length = 1;
    for (const g of groups) $('f-group').add(new Option(g, g));
    for (const t of meta.themes) $('f-theme').add(new Option(t.title, t.id));
  } catch (e) {
    fail(e);
    return;
  }
  $('login').hidden = true;
  $('journal').hidden = false;
  load();
}

$('login-form').addEventListener('submit', ev => {
  ev.preventDefault();
  password = $('password').value;
  try { sessionStorage.setItem(KEY, password); } catch (e) { /* ничего */ }
  enter();
});
$('filters').addEventListener('submit', ev => {
  ev.preventDefault();
  load();
});
$('csv').addEventListener('click', downloadCsv);
for (const b of document.querySelectorAll('.tab')) b.addEventListener('click', () => showTab(b.dataset.tab));
$('settings-form').addEventListener('submit', saveSettings);
$('settings-reset').addEventListener('click', resetSettings);
$('logout').addEventListener('click', () => showLogin());

if (password) enter(); else showLogin();
