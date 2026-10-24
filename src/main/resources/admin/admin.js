'use strict';

// Страница преподавателя: вход по паролю, таблица результатов с фильтрами и выгрузка CSV.
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

async function request(url) {
  const res = await fetch(url, url.startsWith('/api/admin/') ? { headers: { Authorization: authHeader() } } : {});
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

function fail(e) {
  if (e instanceof AuthError) showLogin(e.message);
  else $('error').textContent = e.message;
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
$('logout').addEventListener('click', () => showLogin());

if (password) enter(); else showLogin();
