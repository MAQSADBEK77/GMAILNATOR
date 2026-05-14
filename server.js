const express = require('express');
const os = require('os');

const app = express();
const PORT = 3000;

app.use(express.static(__dirname));

function getLocalIP() {
  for (const nets of Object.values(os.networkInterfaces())) {
    for (const net of nets) {
      if (net.family === 'IPv4' && !net.internal) return net.address;
    }
  }
  return 'localhost';
}

app.listen(PORT, '0.0.0.0', () => {
  const ip = getLocalIP();
  console.clear();
  console.log('\n  ┌─────────────────────────────────────────┐');
  console.log('  │         TEMPMAIL  —  Server ishga tushdi       │');
  console.log('  └─────────────────────────────────────────┘\n');
  console.log(`  Kompyuterdan : http://localhost:${PORT}`);
  console.log(`  Telefondan   : http://${ip}:${PORT}\n`);
  console.log('  ⚠  Telefon va kompyuter bir WiFi\'ga ulangan bo\'lsin!\n');
});
