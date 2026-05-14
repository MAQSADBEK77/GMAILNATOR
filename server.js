const express = require('express');
const crypto  = require('crypto');
const fs      = require('fs');
const path    = require('path');
const fetch   = require('node-fetch');
const os      = require('os');

const app  = express();
const PORT = 4000;

// ── Sozlamalar ──────────────────────────────────────────
const LOGIN    = 'salom';
const PASSWORD = '0007';
const API_KEY  = '03968cdfa2mshe3451f14a06bd97p1f11a0jsn63618796d7f7';
const RH       = 'gmailnator.p.rapidapi.com';
const RB       = `https://${RH}/api`;
const SECRET   = crypto.randomBytes(32).toString('hex'); // har start yangi secret
const TTL      = 24 * 60 * 60 * 1000; // 24 soat

// ── Ma'lumotlar ─────────────────────────────────────────
const DATA = path.join(__dirname, 'devices.json');
function load() {
  try { return JSON.parse(fs.readFileSync(DATA, 'utf8')); }
  catch { return { devices: {} }; }
}
function save(db) { fs.writeFileSync(DATA, JSON.stringify(db, null, 2)); }

// ── Token ───────────────────────────────────────────────
function mkToken(deviceId) {
  const ts  = Date.now().toString();
  const sig = crypto.createHmac('sha256', SECRET).update(`${deviceId}:${ts}`).digest('hex');
  return Buffer.from(`${deviceId}|${ts}|${sig}`).toString('base64url');
}
function checkToken(tok) {
  try {
    const [deviceId, ts, sig] = Buffer.from(tok, 'base64url').toString().split('|');
    const ok = crypto.createHmac('sha256', SECRET).update(`${deviceId}:${ts}`).digest('hex');
    if (sig !== ok) return null;
    if (Date.now() - parseInt(ts) > TTL) return null;
    return deviceId;
  } catch { return null; }
}

// ── Middleware ──────────────────────────────────────────
app.use(express.json());

function auth(req, res, next) {
  const tok = req.headers['x-token'];
  if (!tok) return res.status(401).json({ error: 'Token yo\'q — login qiling' });
  const did = checkToken(tok);
  if (!did) return res.status(401).json({ error: 'Token eskirgan — qayta login qiling' });
  const db  = load();
  const dev = db.devices[did];
  if (!dev) return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });
  if (!dev.allowed) return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });
  req.did = did;
  next();
}

// ── Login ───────────────────────────────────────────────
app.post('/login', (req, res) => {
  const { login, password, deviceId, deviceName } = req.body || {};
  if (login !== LOGIN || password !== PASSWORD)
    return res.status(401).json({ error: 'Login yoki parol noto\'g\'ri' });
  if (!deviceId) return res.status(400).json({ error: 'deviceId yo\'q' });

  const db = load();
  if (!db.devices[deviceId]) {
    db.devices[deviceId] = {
      name: deviceName || 'Yangi qurilma',
      allowed: false,
      firstSeen: new Date().toISOString(),
      lastSeen: new Date().toISOString(),
      stats: {}
    };
    save(db);
    return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan (yangi qurilma — admin paneldan ruxsat bering)' });
  }

  db.devices[deviceId].lastSeen = new Date().toISOString();
  if (deviceName) db.devices[deviceId].name = deviceName;
  save(db);

  if (!db.devices[deviceId].allowed)
    return res.status(403).json({ error: 'Maqsadbek ruxsat bermagan' });

  const token = mkToken(deviceId);
  res.json({ token, expiresIn: TTL });
});

// ── Gmailnator proxy ────────────────────────────────────
async function gn(method, path2, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json', 'x-rapidapi-host': RH, 'x-rapidapi-key': API_KEY } };
  if (body) opts.body = JSON.stringify(body);
  const r = await fetch(RB + path2, opts);
  const d = await r.json();
  return { status: r.status, data: d };
}

app.post('/generate', auth, async (req, res) => {
  try {
    const { status, data } = await gn('POST', '/emails/generate', {
      type: ['public_gmail_plus', 'public_gmail_dot', 'private_gmail_plus', 'private_gmail_dot']
    });
    if (data.status === 'success') {
      const db = load();
      const today = new Date().toISOString().slice(0, 10);
      const dev = db.devices[req.did];
      dev.stats[today] = (dev.stats[today] || 0) + 1;
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

// ── Admin API (faqat localhost) ─────────────────────────
function adminOnly(req, res, next) {
  const ip = req.ip || '';
  if (ip.includes('127.0.0.1') || ip.includes('::1')) return next();
  res.status(403).send('Admin panel faqat kompyuterdan ochiladi');
}

app.get('/admin', adminOnly, (req, res) => res.sendFile(path.join(__dirname, 'admin.html')));

app.get('/admin/data', adminOnly, (req, res) => {
  const db = load();
  const today = new Date().toISOString().slice(0, 10);
  const list = Object.entries(db.devices).map(([id, d]) => ({
    id,
    name: d.name,
    allowed: d.allowed,
    firstSeen: d.firstSeen,
    lastSeen: d.lastSeen,
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
