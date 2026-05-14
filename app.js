const fetch = require('node-fetch');
const { exec } = require('child_process');

const API = 'https://api.mail.tm';
const AUTO_INTERVAL = 15; // soniya

// ANSI ranglar
const C = {
  reset:   '\x1b[0m',
  bold:    '\x1b[1m',
  dim:     '\x1b[2m',
  red:     '\x1b[31m',
  green:   '\x1b[32m',
  yellow:  '\x1b[33m',
  blue:    '\x1b[34m',
  cyan:    '\x1b[36m',
  gray:    '\x1b[90m',
  bgGreen: '\x1b[42m\x1b[30m',
  bgRed:   '\x1b[41m\x1b[37m',
};

let state = {
  token:     '',
  email:     '',
  status:    '',
  statusOk:  true,
  lastCode:  '',
  busy:      false,
  countdown: 0,
  msgSeen:   new Set(),
};

let autoTimer = null;

// ─────────────────────────────────────────────────────────
// Render
// ─────────────────────────────────────────────────────────
function render() {
  const cols = Math.min(process.stdout.columns || 60, 62);
  const LINE = '─'.repeat(cols - 4);

  process.stdout.write('\x1Bc'); // clear screen

  console.log('');
  console.log(`  ${C.cyan}${C.bold}┌${LINE}┐${C.reset}`);
  console.log(`  ${C.cyan}${C.bold}│${center('📧  TEMPMAIL  —  Yarim Avtomatik', cols - 4)}│${C.reset}`);
  console.log(`  ${C.cyan}${C.bold}└${LINE}┘${C.reset}`);
  console.log('');

  // Email
  if (state.email) {
    console.log(`  ${C.gray}Email  :${C.reset}  ${C.bold}${C.cyan}${state.email}${C.reset}  ${C.gray}← clipboard'da${C.reset}`);
  } else {
    console.log(`  ${C.gray}Email  :  hali yaratilmagan${C.reset}`);
  }

  // Kod
  if (state.lastCode) {
    console.log(`  ${C.gray}Kod    :${C.reset}  ${C.bgGreen} ${state.lastCode} ${C.reset}  ${C.gray}← clipboard'da${C.reset}`);
  } else {
    console.log(`  ${C.gray}Kod    :  —${C.reset}`);
  }

  console.log('');
  console.log(`  ${C.gray}${LINE}${C.reset}`);
  console.log(`  ${C.bold}[1]${C.reset}  Yangi email ol  ${C.gray}(clipboard'ga nusxalanadi)${C.reset}`);
  console.log(`  ${C.bold}[2]${C.reset}  Hozir tekshir   ${C.gray}(kod topilsa clipboard'ga)${C.reset}`);
  console.log(`  ${C.bold}[Q]${C.reset}  Chiqish`);
  console.log(`  ${C.gray}${LINE}${C.reset}`);
  console.log('');

  // Status
  if (state.busy) {
    console.log(`  ${C.yellow}⟳  Yuklanmoqda...${C.reset}`);
  } else if (state.status) {
    const icon = state.statusOk ? '✓' : '✗';
    const col  = state.statusOk ? C.green : C.red;
    console.log(`  ${col}${icon}  ${state.status}${C.reset}`);
  }

  // Countdown
  if (!state.busy && state.countdown > 0) {
    console.log(`  ${C.dim}⟳  Avtomatik tekshiruv: ${state.countdown}s${C.reset}`);
  }

  console.log('');
}

function center(str, width) {
  const pad = Math.max(0, width - stripAnsi(str).length);
  const l = Math.floor(pad / 2);
  const r = pad - l;
  return ' '.repeat(l) + str + ' '.repeat(r);
}

function stripAnsi(s) {
  return s.replace(/\x1b\[[0-9;]*m/g, '');
}

// ─────────────────────────────────────────────────────────
// Clipboard
// ─────────────────────────────────────────────────────────
function copy(text) {
  const safe = text.replace(/'/g, "''"); // PowerShell escape
  exec(`powershell -noprofile -command "Set-Clipboard -Value '${safe}'"`, () => {});
}

// ─────────────────────────────────────────────────────────
// API
// ─────────────────────────────────────────────────────────
async function apiFetch(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  const r = await fetch(API + path, { ...opts, headers });
  if (r.status === 204) return {};
  const ct = r.headers.get('content-type') || '';
  const data = ct.includes('json') ? await r.json() : await r.text();
  if (!r.ok) throw new Error(
    data?.['hydra:description'] || data?.detail || `HTTP ${r.status}`
  );
  return data;
}

// ─────────────────────────────────────────────────────────
// Email yaratish
// ─────────────────────────────────────────────────────────
async function generateEmail() {
  if (state.busy) return;
  state.busy = true;
  state.lastCode = '';
  state.msgSeen.clear();
  stopAuto();
  render();

  try {
    const dr = await apiFetch('/domains?page=1');
    const domains = dr['hydra:member'];
    if (!domains?.length) throw new Error('Domen topilmadi');

    const dom  = domains[0].domain;
    const user = Math.random().toString(36).slice(2, 10);
    const pass = Math.random().toString(36).slice(2, 18) + 'Aa1!';
    const addr = `${user}@${dom}`;

    await apiFetch('/accounts', {
      method: 'POST',
      body: JSON.stringify({ address: addr, password: pass }),
    });

    const tr = await apiFetch('/token', {
      method: 'POST',
      body: JSON.stringify({ address: addr, password: pass }),
    });

    state.token = tr.token;
    state.email = addr;
    copy(addr);
    state.status  = `Email clipboard'ga nusxalandi!`;
    state.statusOk = true;
    startAuto();
  } catch (e) {
    state.status  = `Email yaratishda xato: ${e.message}`;
    state.statusOk = false;
  } finally {
    state.busy = false;
    render();
  }
}

// ─────────────────────────────────────────────────────────
// Kod qidirish
// ─────────────────────────────────────────────────────────
function extractCode(text) {
  if (!text) return null;
  const t = text.replace(/<[^>]+>/g, ' '); // HTML teglarni olib tashlash

  const patterns = [
    // Telegram, Google va boshqa xizmatlar
    /(?:login|verification|confirm|tasdiqlash|code|kod)[^\d]*(\d{4,8})/i,
    /(\d{5,6})\s+(?:is your|bu sizning|your)/i,
    /\b(\d{5,6})\b/,  // 5-6 xonali raqam (eng keng tarqalgan)
    /\b(\d{4})\b/,    // 4 xonali
    /\b([A-Z0-9]{6,8})\b/, // Harfli-raqamli kod
  ];

  for (const p of patterns) {
    const m = t.match(p);
    if (m) return m[1];
  }
  return null;
}

async function checkInbox(auto = false) {
  if (!state.token) {
    state.status  = 'Avval [1] bosib email oling';
    state.statusOk = false;
    render();
    return;
  }
  if (state.busy) return;

  state.busy = true;
  render();

  try {
    const res  = await apiFetch('/messages?page=1');
    const msgs = res['hydra:member'] || [];

    if (!msgs.length) {
      state.status  = auto ? 'Xabar kutilmoqda...' : 'Inbox bo\'sh — xabar kutilmoqda';
      state.statusOk = true;
      state.busy = false;
      render();
      return;
    }

    // Yangi xabarlarni ko'rish
    const unseen = msgs.filter(m => !state.msgSeen.has(m.id));
    const target = unseen.length ? unseen[0] : msgs[0];

    const full = await apiFetch('/messages/' + target.id);
    state.msgSeen.add(target.id);

    const raw  = full.text || (full.html?.join(' ') ?? '');
    const code = extractCode(raw);

    if (code) {
      state.lastCode = code;
      copy(code);
      state.status  = `Kod topildi va clipboard'ga nusxalandi → ${code}`;
      state.statusOk = true;
      // Muvaffaqiyatli ovoz (Windows)
      exec('powershell -noprofile -command "[console]::beep(1000,200)"', () => {});
    } else {
      state.status  = `Xabar keldi (${full.from?.address}) — kod topilmadi`;
      state.statusOk = true;
    }
  } catch (e) {
    state.status  = `Xato: ${e.message}`;
    state.statusOk = false;
  } finally {
    state.busy = false;
    render();
  }
}

// ─────────────────────────────────────────────────────────
// Avtomatik tekshiruv
// ─────────────────────────────────────────────────────────
function startAuto() {
  stopAuto();
  state.countdown = AUTO_INTERVAL;
  autoTimer = setInterval(async () => {
    if (state.busy) return;
    state.countdown--;
    if (state.countdown <= 0) {
      state.countdown = AUTO_INTERVAL;
      await checkInbox(true);
    } else {
      render();
    }
  }, 1000);
}

function stopAuto() {
  if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
  state.countdown = 0;
}

// ─────────────────────────────────────────────────────────
// Klaviatura
// ─────────────────────────────────────────────────────────
process.stdin.setRawMode(true);
process.stdin.resume();
process.stdin.setEncoding('utf8');

process.stdin.on('data', async (key) => {
  // Ctrl+C yoki Q — chiqish
  if (key === '' || key === 'q' || key === 'Q') {
    stopAuto();
    process.stdout.write('\x1Bc');
    console.log('\n  Xayr!\n');
    process.exit(0);
  }
  if (key === '1') await generateEmail();
  if (key === '2') await checkInbox(false);
});

// ─────────────────────────────────────────────────────────
// Ishga tushirish
// ─────────────────────────────────────────────────────────
state.status  = '[1] ni bosib email yarating';
state.statusOk = true;
render();
