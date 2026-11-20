/*
 * حساب مواقيت الصلاة فلكياً (بدون إنترنت)
 * مبني على الخوارزمية الفلكية المعروفة (PrayTimes / معادلات موقع الشمس).
 */
(function (root) {
  'use strict';

  const deg = (r) => (r * 180) / Math.PI;
  const rad = (d) => (d * Math.PI) / 180;
  const dsin = (d) => Math.sin(rad(d));
  const dcos = (d) => Math.cos(rad(d));
  const dtan = (d) => Math.tan(rad(d));
  const darcsin = (x) => deg(Math.asin(x));
  const darccos = (x) => deg(Math.acos(x));
  const darctan2 = (y, x) => deg(Math.atan2(y, x));
  const darccot = (x) => deg(Math.atan(1 / x));
  const fix = (a, b) => { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; };
  const fixAngle = (a) => fix(a, 360);
  const fixHour = (a) => fix(a, 24);

  // fajr / isha: زاوية بالدرجات، أو { minutes: n } بعد المغرب
  const METHODS = {
    egypt:   { name: 'الهيئة المصرية العامة للمساحة', fajr: 19.5, isha: 17.5 },
    makkah:  { name: 'أم القرى - مكة المكرمة', fajr: 18.5, isha: { minutes: 90 } },
    mwl:     { name: 'رابطة العالم الإسلامي', fajr: 18, isha: 17 },
    gulf:    { name: 'منطقة الخليج', fajr: 19.5, isha: { minutes: 90 } },
    dubai:   { name: 'الإمارات (دبي)', fajr: 18.2, isha: 18.2 },
    kuwait:  { name: 'الكويت', fajr: 18, isha: 17.5 },
    qatar:   { name: 'قطر', fajr: 18, isha: { minutes: 90 } },
    jordan:  { name: 'الأردن', fajr: 18, isha: 18 },
    algeria: { name: 'الجزائر', fajr: 18, isha: 17 },
    morocco: { name: 'المغرب', fajr: 19, isha: 17 },
    tunisia: { name: 'تونس', fajr: 18, isha: 18 },
    turkey:  { name: 'تركيا (ديانت)', fajr: 18, isha: 17 },
    karachi: { name: 'جامعة العلوم الإسلامية - كراتشي', fajr: 18, isha: 18 },
    isna:    { name: 'أمريكا الشمالية (ISNA)', fajr: 15, isha: 15 },
  };

  function julian(year, month, day) {
    if (month <= 2) { year -= 1; month += 12; }
    const A = Math.floor(year / 100);
    const B = 2 - A + Math.floor(A / 4);
    return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + B - 1524.5;
  }

  function sunPosition(jd) {
    const D = jd - 2451545.0;
    const g = fixAngle(357.529 + 0.98560028 * D);
    const q = fixAngle(280.459 + 0.98564736 * D);
    const L = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
    const e = 23.439 - 0.00000036 * D;
    const RA = darctan2(dcos(e) * dsin(L), dcos(L)) / 15;
    return { declination: darcsin(dsin(e) * dsin(L)), equation: q / 15 - fixHour(RA) };
  }

  /**
   * @param {{year:number,month:number,day:number}} date  التاريخ في المنطقة الزمنية للموقع
   * @param {number} lat
   * @param {number} lng
   * @param {number} tzOffset  فرق التوقيت بالساعات عن UTC في ذلك اليوم
   * @param {{method?:string, asr?:'standard'|'hanafi', elevation?:number, adjust?:object}} opts
   * @returns {{fajr,sunrise,dhuhr,asr,maghrib,isha}} أوقات بالساعات العشرية (توقيت محلي)
   */
  function compute(date, lat, lng, tzOffset, opts = {}) {
    const method = METHODS[opts.method] || METHODS.egypt;
    const asrFactor = opts.asr === 'hanafi' ? 2 : 1;
    const elev = opts.elevation || 0;
    const jDate = julian(date.year, date.month, date.day) - lng / (15 * 24);

    const midDay = (t) => fixHour(12 - sunPosition(jDate + t).equation);
    const sunAngleTime = (angle, t, ccw) => {
      const decl = sunPosition(jDate + t).declination;
      const noon = midDay(t);
      const v = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15;
      return noon + (ccw ? -v : v);
    };
    const asrTime = (factor, t) => {
      const decl = sunPosition(jDate + t).declination;
      const angle = -darccot(factor + dtan(Math.abs(lat - decl)));
      return sunAngleTime(angle, t);
    };
    const riseSet = 0.833 + 0.0347 * Math.sqrt(elev);

    // تقديرات أولية كنسبة من اليوم، ثم تكرار للتحسين
    let t = { fajr: 5, sunrise: 6, dhuhr: 12, asr: 13, sunset: 18, isha: 18 };
    for (let i = 0; i < 2; i++) {
      const d = {};
      for (const k in t) d[k] = t[k] / 24;
      t = {
        fajr: sunAngleTime(method.fajr, d.fajr, true),
        sunrise: sunAngleTime(riseSet, d.sunrise, true),
        dhuhr: midDay(d.dhuhr),
        asr: asrTime(asrFactor, d.asr),
        sunset: sunAngleTime(riseSet, d.sunset),
        isha: typeof method.isha === 'number' ? sunAngleTime(method.isha, d.isha) : 18,
      };
    }

    const shift = tzOffset - lng / 15;
    for (const k in t) t[k] += shift;

    // خطوط العرض العالية: طريقة الزاوية (Angle-based)
    const night = fixHour(t.sunrise - t.sunset);
    const fajrMax = (method.fajr / 60) * night;
    if (isNaN(t.fajr) || fixHour(t.sunrise - t.fajr) > fajrMax) t.fajr = t.sunrise - fajrMax;
    if (typeof method.isha === 'number') {
      const ishaMax = (method.isha / 60) * night;
      if (isNaN(t.isha) || fixHour(t.isha - t.sunset) > ishaMax) t.isha = t.sunset + ishaMax;
    } else {
      t.isha = t.sunset + method.isha.minutes / 60;
    }

    const adj = opts.adjust || {};
    const out = {
      fajr: t.fajr,
      sunrise: t.sunrise,
      dhuhr: t.dhuhr,
      asr: t.asr,
      maghrib: t.sunset,
      isha: t.isha,
    };
    for (const k in out) out[k] += (adj[k] || 0) / 60;
    return out;
  }

  const api = { compute, METHODS, julian };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PrayTimes = api;
})(typeof self !== 'undefined' ? self : this);
