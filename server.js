const express = require('express');
const fs      = require('fs');
const path    = require('path');
const fetch   = require('node-fetch');
const os      = require('os');

const app  = express();
const PORT = 4000;

const API_KEY = '03968cdfa2mshe3451f14a06bd97p1f11a0jsn63618796d7f7';
const RH      = 'gmailnator.p.rapidapi.com';
const RB      = `https://${RH}/api`;

const DATA = path.join(__dirname, 'devices.json');
function load() {
  try { return JSON.parse(fs.readFileSync(DATA, 'utf8')); }
  catch { return { devices: {} }; }
}
function save(db) { fs.writeFileSync(DATA, JSON.stringify(db, null, 2)); }

app.use(express.json());

// ── Auth middleware ─────────────────────────────────────
function auth(req, res, next) {
  const did = req.headers['x-device-id'];
  if (!did) return res.status(401).json({ error: 'Qurilma ID yo\'q' });

  const db  = load();
  if (!db.devices[did]) {
    db.devices[did] = {
      name: req.headers['x-device-name'] || 'Yangi qurilma',
      allowed: false,
      firstSeen: new Date().toISOString(),
      lastSeen: new Date().toISOString(),
      stats: {}
    };
    save(db);
    return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });
  }

  db.devices[did].lastSeen = new Date().toISOString();
  const name = req.headers['x-device-name'];
  if (name && db.devices[did].name === 'Yangi qurilma') db.devices[did].name = name;
  save(db);

  if (!db.devices[did].allowed)
    return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });

  req.did = did;
  next();
}

// ── Token tekshirish (app kirish kaliti) ─────────────────
app.post('/verify-token', (req, res) => {
  const { token } = req.body || {};
  if (!token) return res.status(400).json({ error: 'Token yo\'q' });
  if (token === API_KEY) {
    res.json({ ok: true });
  } else {
    res.status(401).json({ error: 'Noto\'g\'ri kalit' });
  }
});

// ── Connect (qurilmani ro'yxatdan o'tkazish) ────────────
app.post('/connect', (req, res) => {
  const did  = req.headers['x-device-id'];
  const name = req.headers['x-device-name'] || 'Yangi qurilma';
  if (!did) return res.status(400).json({ error: 'ID yo\'q' });

  const db = load();
  if (!db.devices[did]) {
    db.devices[did] = { name, allowed: false, firstSeen: new Date().toISOString(), lastSeen: new Date().toISOString(), stats: {} };
    save(db);
    return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });
  }
  db.devices[did].lastSeen = new Date().toISOString();
  if (name && db.devices[did].name === 'Yangi qurilma') db.devices[did].name = name;
  save(db);

  if (!db.devices[did].allowed)
    return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });

  res.json({ ok: true });
});

// ── Gmailnator proxy ────────────────────────────────────
async function gn(method, p, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json', 'x-rapidapi-host': RH, 'x-rapidapi-key': API_KEY } };
  if (body) opts.body = JSON.stringify(body);
  const r = await fetch(RB + p, opts);
  return { status: r.status, data: await r.json() };
}

app.post('/generate', auth, async (req, res) => {
  try {
    const { status, data } = await gn('POST', '/emails/generate', {
      type: ['public_gmail_plus', 'public_gmail_dot', 'private_gmail_plus', 'private_gmail_dot']
    });
    if (data.status === 'success') {
      const db = load(), today = new Date().toISOString().slice(0, 10);
      db.devices[req.did].stats[today] = (db.devices[req.did].stats[today] || 0) + 1;
      save(db);
    }
    res.status(status).json(data);
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post('/inbox', auth, async (req, res) => {
  try {
    const { status, data } = await gn('POST', '/inbox', req.body);
    res.status(status).json(data);
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/message/:id', auth, async (req, res) => {
  try {
    const { status, data } = await gn('GET', `/inbox/${encodeURIComponent(req.params.id)}`);
    res.status(status).json(data);
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Admin (faqat localhost) ─────────────────────────────
function adminOnly(req, res, next) {
  const ip = req.ip || '';
  if (ip.includes('127.0.0.1') || ip.includes('::1')) return next();
  res.status(403).send('Admin panel faqat kompyuterdan');
}

app.get('/admin', adminOnly, (req, res) => res.sendFile(path.join(__dirname, 'admin.html')));

app.get('/admin/data', adminOnly, (req, res) => {
  const db = load(), today = new Date().toISOString().slice(0, 10);
  const list = Object.entries(db.devices).map(([id, d]) => ({
    id, name: d.name, allowed: d.allowed,
    firstSeen: d.firstSeen, lastSeen: d.lastSeen,
    todayEmails: d.stats?.[today] || 0,
    totalEmails: Object.values(d.stats || {}).reduce((a, b) => a + b, 0),
    stats: d.stats || {}
  }));
  res.json({ devices: list, today });
});

app.post('/admin/allow/:id', adminOnly, (req, res) => {
  const db = load();
  if (db.devices[req.params.id]) { db.devices[req.params.id].allowed = true; save(db); }
  res.json({ ok: true });
});

app.post('/admin/block/:id', adminOnly, (req, res) => {
  const db = load();
  if (db.devices[req.params.id]) { db.devices[req.params.id].allowed = false; save(db); }
  res.json({ ok: true });
});

app.post('/admin/rename/:id', adminOnly, (req, res) => {
  const db = load();
  if (db.devices[req.params.id]) { db.devices[req.params.id].name = req.body.name; save(db); }
  res.json({ ok: true });
});

// ── Start ───────────────────────────────────────────────
app.listen(PORT, '0.0.0.0', () => {
  let ip = 'localhost';
  for (const nets of Object.values(os.networkInterfaces()))
    for (const n of nets)
      if (n.family === 'IPv4' && !n.internal) { ip = n.address; break; }

  console.clear();
  console.log('\n  ╔══════════════════════════════════════════╗');
  console.log('  ║      GMAILNATOR ADMIN SERVER             ║');
  console.log('  ╚══════════════════════════════════════════╝\n');
  console.log(`  Admin panel : http://localhost:${PORT}/admin`);
  console.log(`  Telefon IP  : ${ip}:${PORT}`);
  console.log('\n  Server ishlayapti. Yopish: Ctrl+C\n');
  require('child_process').exec(`start http://localhost:${PORT}/admin`);
});
