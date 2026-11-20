// node test/praytimes.test.js
const assert = require('assert');
const P = require('../js/praytimes.js');

const toMin = (h) => Math.round(h * 60);
const hm = (s) => { const [h, m] = s.split(':').map(Number); return h * 60 + m; };

function check(label, got, expected, tolerance = 2) {
  for (const k of Object.keys(expected)) {
    const diff = Math.abs(toMin(got[k]) - hm(expected[k]));
    assert(diff <= tolerance, `${label} ${k}: expected ${expected[k]}, diff ${diff} min`);
  }
  console.log('✓', label);
}

// مواقيت منشورة (تقويم الهيئة المصرية / أم القرى) — بفارق دقيقتين كحد أقصى
check('القاهرة 2026-11-20 (الهيئة المصرية)',
  P.compute({ year: 2026, month: 11, day: 20 }, 30.0444, 31.2357, 2, { method: 'egypt' }),
  { fajr: '04:54', sunrise: '06:24', dhuhr: '11:41', asr: '14:37', maghrib: '16:57', isha: '18:18' });

check('مكة 2026-11-20 (أم القرى)',
  P.compute({ year: 2026, month: 11, day: 20 }, 21.4225, 39.8262, 3, { method: 'makkah' }),
  { fajr: '05:16', sunrise: '06:36', dhuhr: '12:06', asr: '15:16', maghrib: '17:37', isha: '19:07' });

// العصر الحنفي متأخر عن الجمهور
const std = P.compute({ year: 2026, month: 11, day: 20 }, 30.0444, 31.2357, 2, { method: 'egypt' });
const han = P.compute({ year: 2026, month: 11, day: 20 }, 30.0444, 31.2357, 2, { method: 'egypt', asr: 'hanafi' });
assert(han.asr > std.asr + 0.5, 'hanafi asr should be later');
console.log('✓ العصر الحنفي');

// التعديل اليدوي
const adj = P.compute({ year: 2026, month: 11, day: 20 }, 30.0444, 31.2357, 2, { method: 'egypt', adjust: { fajr: 3 } });
assert.strictEqual(toMin(adj.fajr) - toMin(std.fajr), 3);
console.log('✓ التعديل اليدوي');

// خطوط العرض العالية: كل المواقيت أرقام صحيحة ومرتبة
const ny = P.compute({ year: 2026, month: 6, day: 21 }, 64.1466, -21.9426, 0, { method: 'mwl' }); // ريكيافيك
const order = ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'];
order.forEach((k) => assert(Number.isFinite(ny[k]), `high-lat ${k} is NaN`));
for (let i = 1; i < order.length; i++) assert(ny[order[i]] > ny[order[i - 1]], `high-lat order ${order[i]}`);
console.log('✓ خطوط العرض العالية');

console.log('\nكل الاختبارات نجحت');
