/* تطبيق مواقيت الصلاة مع الأذان */
(function () {
  'use strict';

  const PRAYERS = [
    { key: 'fajr', name: 'الفجر', azan: true },
    { key: 'sunrise', name: 'الشروق', azan: false },
    { key: 'dhuhr', name: 'الظهر', azan: true },
    { key: 'asr', name: 'العصر', azan: true },
    { key: 'maghrib', name: 'المغرب', azan: true },
    { key: 'isha', name: 'العشاء', azan: true },
  ];

  const CITIES = [
    { name: 'القاهرة', lat: 30.0444, lng: 31.2357, tz: 'Africa/Cairo', method: 'egypt' },
    { name: 'الإسكندرية', lat: 31.2001, lng: 29.9187, tz: 'Africa/Cairo', method: 'egypt' },
    { name: 'مكة المكرمة', lat: 21.4225, lng: 39.8262, tz: 'Asia/Riyadh', method: 'makkah' },
    { name: 'المدينة المنورة', lat: 24.4672, lng: 39.6111, tz: 'Asia/Riyadh', method: 'makkah' },
    { name: 'الرياض', lat: 24.7136, lng: 46.6753, tz: 'Asia/Riyadh', method: 'makkah' },
    { name: 'جدة', lat: 21.4858, lng: 39.1925, tz: 'Asia/Riyadh', method: 'makkah' },
    { name: 'دبي', lat: 25.2048, lng: 55.2708, tz: 'Asia/Dubai', method: 'dubai' },
    { name: 'أبوظبي', lat: 24.4539, lng: 54.3773, tz: 'Asia/Dubai', method: 'dubai' },
    { name: 'الكويت', lat: 29.3759, lng: 47.9774, tz: 'Asia/Kuwait', method: 'kuwait' },
    { name: 'الدوحة', lat: 25.2854, lng: 51.531, tz: 'Asia/Qatar', method: 'qatar' },
    { name: 'المنامة', lat: 26.2285, lng: 50.586, tz: 'Asia/Bahrain', method: 'gulf' },
    { name: 'مسقط', lat: 23.588, lng: 58.3829, tz: 'Asia/Muscat', method: 'gulf' },
    { name: 'عمّان', lat: 31.9454, lng: 35.9284, tz: 'Asia/Amman', method: 'jordan' },
    { name: 'القدس', lat: 31.7683, lng: 35.2137, tz: 'Asia/Jerusalem', method: 'jordan' },
    { name: 'بيروت', lat: 33.8938, lng: 35.5018, tz: 'Asia/Beirut', method: 'mwl' },
    { name: 'دمشق', lat: 33.5138, lng: 36.2765, tz: 'Asia/Damascus', method: 'egypt' },
    { name: 'بغداد', lat: 33.3152, lng: 44.3661, tz: 'Asia/Baghdad', method: 'mwl' },
    { name: 'الخرطوم', lat: 15.5007, lng: 32.5599, tz: 'Africa/Khartoum', method: 'egypt' },
    { name: 'طرابلس', lat: 32.8872, lng: 13.1913, tz: 'Africa/Tripoli', method: 'egypt' },
    { name: 'تونس', lat: 36.8065, lng: 10.1815, tz: 'Africa/Tunis', method: 'tunisia' },
    { name: 'الجزائر', lat: 36.7538, lng: 3.0588, tz: 'Africa/Algiers', method: 'algeria' },
    { name: 'الرباط', lat: 34.0209, lng: -6.8416, tz: 'Africa/Casablanca', method: 'morocco' },
    { name: 'الدار البيضاء', lat: 33.5731, lng: -7.5898, tz: 'Africa/Casablanca', method: 'morocco' },
    { name: 'صنعاء', lat: 15.3694, lng: 44.191, tz: 'Asia/Aden', method: 'makkah' },
    { name: 'إسطنبول', lat: 41.0082, lng: 28.9784, tz: 'Europe/Istanbul', method: 'turkey' },
    { name: 'لندن', lat: 51.5074, lng: -0.1278, tz: 'Europe/London', method: 'mwl' },
    { name: 'باريس', lat: 48.8566, lng: 2.3522, tz: 'Europe/Paris', method: 'mwl' },
    { name: 'برلين', lat: 52.52, lng: 13.405, tz: 'Europe/Berlin', method: 'mwl' },
    { name: 'نيويورك', lat: 40.7128, lng: -74.006, tz: 'America/New_York', method: 'isna' },
  ];

  const $ = (id) => document.getElementById(id);
  const deviceTz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  let settings = Store.loadSettings();
  let today = null;           // { dateKey, instants: {fajr: ms, ...} }
  let audioUnlocked = false;
  let objectUrls = [];
  const fired = new Set();

  // ---------- المنطقة الزمنية ----------
  function partsInTz(ms, tz) {
    const f = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hourCycle: 'h23',
      year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric', minute: 'numeric', second: 'numeric',
    });
    const p = {};
    for (const { type, value } of f.formatToParts(new Date(ms))) p[type] = +value;
    return p;
  }
  function tzOffsetHours(ms, tz) {
    const p = partsInTz(ms, tz);
    const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
    return (asUtc - Math.floor(ms / 1000) * 1000) / 3600000;
  }

  // ---------- حساب اليوم ----------
  function computeFor(ms) {
    const loc = settings.location;
    const tz = loc.tz || deviceTz;
    const p = partsInTz(ms, tz);
    const date = { year: p.year, month: p.month, day: p.day };
    const noonUtc = Date.UTC(p.year, p.month - 1, p.day, 12);
    const offset = tzOffsetHours(noonUtc, tz);
    const hours = PrayTimes.compute(date, loc.lat, loc.lng, offset, {
      method: settings.method, asr: settings.asr, adjust: settings.adjust,
    });
    const base = Date.UTC(p.year, p.month - 1, p.day);
    const instants = {};
    // تقريب لأقرب دقيقة كما في التقاويم المنشورة
    for (const k in hours) instants[k] = Math.round((base + (hours[k] - offset) * 3600000) / 60000) * 60000;
    return { dateKey: `${p.year}-${p.month}-${p.day}`, instants, tz };
  }

  function fmtTime(ms, tz) {
    return new Intl.DateTimeFormat('ar-EG', { timeZone: tz, hour: 'numeric', minute: '2-digit' }).format(ms);
  }

  // ---------- العرض ----------
  function renderTimes() {
    const list = $('times');
    list.innerHTML = '';
    for (const pr of PRAYERS) {
      const li = document.createElement('li');
      li.dataset.key = pr.key;
      const left = document.createElement('div');
      left.className = 'left';
      if (pr.azan) {
        const bell = document.createElement('button');
        bell.className = 'bell' + (settings.enabled[pr.key] ? '' : ' off');
        bell.textContent = settings.enabled[pr.key] ? '🔔' : '🔕';
        bell.title = settings.enabled[pr.key] ? 'الأذان مفعّل' : 'الأذان متوقف';
        bell.setAttribute('aria-label', `أذان ${pr.name}: ${bell.title}`);
        bell.onclick = () => {
          settings.enabled[pr.key] = !settings.enabled[pr.key];
          Store.saveSettings(settings);
          renderTimes(); renderSettings();
        };
        left.appendChild(bell);
      }
      const name = document.createElement('span');
      name.className = 'pname';
      name.textContent = pr.name;
      left.appendChild(name);
      const time = document.createElement('span');
      time.className = 'ptime';
      time.textContent = fmtTime(today.instants[pr.key], today.tz);
      li.append(left, time);
      list.appendChild(li);
    }
  }

  function renderDates() {
    const now = Date.now();
    const tz = today.tz;
    try {
      $('hijri-date').textContent = new Intl.DateTimeFormat('ar-SA-u-ca-islamic-umalqura', {
        timeZone: tz, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
      }).format(now);
    } catch (e) { $('hijri-date').textContent = ''; }
    $('greg-date').textContent = new Intl.DateTimeFormat('ar-EG', {
      timeZone: tz, day: 'numeric', month: 'long', year: 'numeric',
    }).format(now);
  }

  function findNext(now) {
    for (const pr of PRAYERS) {
      if (pr.key === 'sunrise') continue;
      if (today.instants[pr.key] > now) return { pr, at: today.instants[pr.key] };
    }
    const tomorrow = computeFor(now + 86400000);
    return { pr: PRAYERS[0], at: tomorrow.instants.fajr, tz: tomorrow.tz };
  }

  function tick() {
    const now = Date.now();
    if (!today || computeFor(now).dateKey !== today.dateKey) {
      today = computeFor(now);
      renderTimes(); renderDates();
    }

    // تشغيل الأذان عند دخول الوقت (نافذة 3 دقائق لتعويض تأخر المؤقت في الخلفية)
    for (const pr of PRAYERS) {
      if (!pr.azan || !settings.enabled[pr.key]) continue;
      const at = today.instants[pr.key];
      const key = `${today.dateKey}:${pr.key}`;
      if (now >= at && now - at < 180000 && !fired.has(key)) {
        fired.add(key);
        triggerAzan(pr);
      }
    }

    const next = findNext(now);
    $('next-name').textContent = next.pr.name;
    $('next-time').textContent = fmtTime(next.at, today.tz);
    let s = Math.max(0, Math.floor((next.at - now) / 1000));
    const h = Math.floor(s / 3600); s %= 3600;
    const m = Math.floor(s / 60); s %= 60;
    $('countdown').textContent = [h, m, s].map((x) => String(x).padStart(2, '0')).join(':');

    // تمييز الصلاة الحالية والقادمة
    let current = null;
    for (const pr of PRAYERS) if (today.instants[pr.key] <= now) current = pr.key;
    for (const li of $('times').children) {
      li.classList.toggle('current', li.dataset.key === current);
      li.classList.toggle('next-up', li.dataset.key === next.pr.key && next.at - now < 86400000 && !next.tz);
    }
    document.title = `${next.pr.name} بعد ${$('countdown').textContent} · مواقيت الصلاة`;
  }

  // ---------- الصوت ----------
  const player = new Audio();
  player.preload = 'auto';
  let toneCtx = null;
  let toneTimer = null;

  const muezzinById = (id) => MUEZZINS.find((m) => m.id === id) || MUEZZINS[0];

  async function sourcesFor(id) {
    const list = [];
    const blob = await Store.getAudio(id);
    if (blob) {
      const url = URL.createObjectURL(blob);
      objectUrls.push(url);
      list.push({ url, kind: 'ملف مرفوع' });
    }
    if (settings.customUrls[id]) list.push({ url: settings.customUrls[id], kind: 'رابط' });
    if (await hasLocalFile(id)) list.push({ url: `audio/${id}.mp3`, kind: 'ملف محلي' });
    const m = MUEZZINS.find((x) => x.id === id);
    if (m && m.url) list.push({ url: m.url, kind: 'من الإنترنت' });
    return list;
  }

  function tryPlay(url) {
    return new Promise((resolve) => {
      const cleanup = () => { player.removeEventListener('playing', ok); player.removeEventListener('error', bad); clearTimeout(t); };
      const ok = () => { cleanup(); resolve(true); };
      const bad = () => { cleanup(); resolve(false); };
      const t = setTimeout(bad, 8000);
      player.addEventListener('playing', ok);
      player.addEventListener('error', bad);
      player.src = url;
      player.volume = settings.volume;
      player.play().catch(bad);
    });
  }

  async function playMuezzin(id) {
    stopPlayback();
    for (const src of await sourcesFor(id)) {
      if (await tryPlay(src.url)) return src.kind;
    }
    playFallbackTone();
    return null;
  }

  // نغمة تنبيه بديلة إذا لم يتوفر ملف الأذان
  function playFallbackTone() {
    try {
      toneCtx = toneCtx || new (window.AudioContext || window.webkitAudioContext)();
      toneCtx.resume();
      const notes = [523.25, 659.25, 783.99, 659.25, 523.25];
      let i = 0;
      const ring = () => {
        const t0 = toneCtx.currentTime;
        notes.forEach((f, n) => {
          const o = toneCtx.createOscillator();
          const g = toneCtx.createGain();
          o.type = 'sine'; o.frequency.value = f;
          g.gain.setValueAtTime(0.0001, t0 + n * 0.45);
          g.gain.exponentialRampToValueAtTime(0.3 * settings.volume + 0.0001, t0 + n * 0.45 + 0.05);
          g.gain.exponentialRampToValueAtTime(0.0001, t0 + n * 0.45 + 0.42);
          o.connect(g).connect(toneCtx.destination);
          o.start(t0 + n * 0.45); o.stop(t0 + n * 0.45 + 0.45);
        });
        if (++i < 3) toneTimer = setTimeout(ring, 3000);
        else toneTimer = setTimeout(stopAudio, 2600);
      };
      ring();
    } catch (e) { /* لا يوجد دعم للصوت */ }
  }

  function stopPlayback() {
    player.pause();
    player.removeAttribute('src');
    player.load();
    clearTimeout(toneTimer);
    objectUrls.forEach((u) => URL.revokeObjectURL(u));
    objectUrls = [];
  }

  function stopAudio() {
    stopPlayback();
    $('azan-overlay').hidden = true;
    $('preview').hidden = false;
    $('stop').hidden = true;
  }
  player.addEventListener('ended', stopAudio);

  async function triggerAzan(pr) {
    const id = pr.key === 'fajr' && settings.fajrMuezzin !== 'same' ? settings.fajrMuezzin : settings.muezzin;
    const m = muezzinById(id);
    $('azan-prayer').textContent = pr.name;
    $('azan-muezzin').textContent = m.name;
    $('azan-overlay').hidden = false;
    notify(`حان الآن موعد أذان ${pr.name}`, `بصوت ${m.name}`);
    const kind = await playMuezzin(id);
    if (!kind) $('azan-muezzin').textContent = `${m.name} — لا يوجد ملف صوتي، تم تشغيل تنبيه بديل`;
  }

  async function unlockAudio() {
    // المتصفحات تمنع التشغيل التلقائي قبل تفاعل المستخدم؛ هذا الزر يفتح الصوت
    try {
      player.src = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA=';
      await player.play().catch(() => {});
      player.pause();
      toneCtx = toneCtx || new (window.AudioContext || window.webkitAudioContext)();
      await toneCtx.resume();
    } catch (e) { /* ignore */ }
    audioUnlocked = true;
    const btn = $('unlock-audio');
    btn.textContent = '✓ الأذان التلقائي مفعّل';
    btn.classList.add('done');
    $('audio-status').textContent = 'اترك الصفحة مفتوحة (ولو في الخلفية) ليُرفع الأذان في وقته.';
    if (settings.notify) requestNotify();
  }

  // ---------- الإشعارات ----------
  async function requestNotify() {
    if (!('Notification' in window)) return false;
    if (Notification.permission === 'granted') return true;
    return (await Notification.requestPermission()) === 'granted';
  }
  async function notify(title, body) {
    if (!settings.notify || !('Notification' in window) || Notification.permission !== 'granted') return;
    try {
      const reg = navigator.serviceWorker && (await navigator.serviceWorker.getRegistration());
      if (reg) reg.showNotification(title, { body, icon: 'icon.svg', tag: 'azan', renotify: true });
      else new Notification(title, { body, icon: 'icon.svg' });
    } catch (e) { /* ignore */ }
  }

  // ---------- الإعدادات ----------
  function fillSelect(sel, items, value) {
    for (const [v, label] of items) {
      const o = document.createElement('option');
      o.value = v; o.textContent = label;
      sel.appendChild(o);
    }
    sel.value = value;
  }

  const localFileCache = new Map();
  function hasLocalFile(id) {
    if (!localFileCache.has(id)) {
      localFileCache.set(id, fetch(`audio/${id}.mp3`, { method: 'HEAD', cache: 'no-store' })
        .then((r) => r.ok).catch(() => false)); // file:// أو غير متاح
    }
    return localFileCache.get(id);
  }

  async function describeSource(id) {
    if (await Store.getAudio(id)) return 'ملف مرفوع';
    if (settings.customUrls[id]) return 'رابط';
    if (await hasLocalFile(id)) return 'ملف محلي';
    const m = MUEZZINS.find((x) => x.id === id);
    if (m && m.url) return 'من الإنترنت';
    return null;
  }

  async function renderMuezzinSource() {
    const kind = await describeSource(settings.muezzin);
    $('muezzin-source').textContent = kind
      ? `مصدر الصوت: ${kind}`
      : 'لا يوجد ملف صوتي لهذا المؤذن بعد — أضفه من الإعدادات ← أصوات المؤذنين (سيُستخدم تنبيه بديل حتى ذلك الحين).';
  }

  function renderSettings() {
    $('lat').value = settings.location ? settings.location.lat : '';
    $('lng').value = settings.location ? settings.location.lng : '';
    $('method-select').value = settings.method;
    $('asr-select').value = settings.asr;
    $('fajr-muezzin-select').value = settings.fajrMuezzin;
    $('volume').value = settings.volume;
    $('notify').checked = settings.notify;

    const en = $('enabled-list');
    en.innerHTML = '';
    for (const pr of PRAYERS.filter((p) => p.azan)) {
      const l = document.createElement('label');
      l.className = 'check';
      const c = document.createElement('input');
      c.type = 'checkbox'; c.checked = settings.enabled[pr.key];
      c.onchange = () => { settings.enabled[pr.key] = c.checked; save(); };
      l.append(c, ` أذان ${pr.name}`);
      en.appendChild(l);
    }

    const adj = $('adjust-list');
    adj.innerHTML = '';
    for (const pr of PRAYERS) {
      const l = document.createElement('label');
      const i = document.createElement('input');
      i.type = 'number'; i.step = '1'; i.value = settings.adjust[pr.key] || 0;
      i.onchange = () => { settings.adjust[pr.key] = parseInt(i.value, 10) || 0; save(true); };
      l.append(pr.name, i);
      adj.appendChild(l);
    }
  }

  async function renderAudioManager() {
    const ul = $('audio-manager');
    ul.innerHTML = '';
    for (const m of MUEZZINS) {
      const li = document.createElement('li');
      const head = document.createElement('div');
      head.className = 'row';
      const title = document.createElement('strong');
      title.textContent = m.name;
      const badge = document.createElement('span');
      const kind = await describeSource(m.id);
      badge.className = 'badge' + (kind ? '' : ' none');
      badge.textContent = kind || 'لا يوجد صوت';
      head.append(title, badge);

      const row = document.createElement('div');
      row.className = 'row';
      const upload = document.createElement('label');
      upload.className = 'secondary file-btn';
      upload.textContent = 'رفع ملف';
      const file = document.createElement('input');
      file.type = 'file'; file.accept = 'audio/*';
      file.onchange = async () => {
        if (!file.files[0]) return;
        await Store.putAudio(m.id, file.files[0]);
        renderAudioManager(); renderMuezzinSource();
      };
      upload.appendChild(file);

      const url = document.createElement('input');
      url.type = 'url'; url.placeholder = 'أو رابط مباشر لملف MP3';
      url.value = settings.customUrls[m.id] || '';
      url.onchange = () => {
        const v = url.value.trim();
        if (v) settings.customUrls[m.id] = v; else delete settings.customUrls[m.id];
        save(); renderAudioManager();
      };

      const play = document.createElement('button');
      play.type = 'button'; play.className = 'secondary'; play.textContent = '▶';
      play.title = 'استماع';
      play.onclick = () => { playMuezzin(m.id); $('preview').hidden = true; $('stop').hidden = false; };

      row.append(upload, url, play);
      if (kind === 'ملف مرفوع') {
        const del = document.createElement('button');
        del.type = 'button'; del.className = 'secondary'; del.textContent = 'حذف الملف';
        del.onclick = async () => { await Store.deleteAudio(m.id); renderAudioManager(); renderMuezzinSource(); };
        row.appendChild(del);
      }
      li.append(head, row);
      if (m.note) {
        const note = document.createElement('span');
        note.className = 'muted small'; note.textContent = m.note;
        li.appendChild(note);
      }
      ul.appendChild(li);
    }
  }

  function save(recompute) {
    Store.saveSettings(settings);
    if (recompute && settings.location) { today = null; tick(); }
  }

  function setLocation(loc, method) {
    settings.location = loc;
    if (method) settings.method = method;
    $('location-name').textContent = loc.name;
    renderSettings();
    save(true);
  }

  function useGps() {
    if (!navigator.geolocation) { alert('المتصفح لا يدعم تحديد الموقع.'); return; }
    $('location-name').textContent = 'جارٍ تحديد الموقع…';
    navigator.geolocation.getCurrentPosition(
      (pos) => setLocation({
        lat: +pos.coords.latitude.toFixed(4), lng: +pos.coords.longitude.toFixed(4),
        name: 'موقعي الحالي', tz: deviceTz,
      }),
      () => {
        $('location-name').textContent = settings.location ? settings.location.name : 'لم يتم تحديد الموقع';
        alert('تعذّر تحديد الموقع. اختر مدينتك من الإعدادات.');
        $('settings').showModal();
      },
      { enableHighAccuracy: false, timeout: 15000, maximumAge: 3600000 },
    );
  }

  function bind() {
    const muezzinItems = MUEZZINS.map((m) => [m.id, m.name]);
    fillSelect($('muezzin-select'), muezzinItems, settings.muezzin);
    fillSelect($('fajr-muezzin-select'), muezzinItems, settings.fajrMuezzin);
    fillSelect($('method-select'), Object.entries(PrayTimes.METHODS).map(([k, v]) => [k, v.name]), settings.method);
    fillSelect($('city-select'), CITIES.map((c, i) => [String(i), c.name]), '');

    $('muezzin-select').onchange = (e) => { settings.muezzin = e.target.value; save(); renderMuezzinSource(); };
    $('fajr-muezzin-select').onchange = (e) => { settings.fajrMuezzin = e.target.value; save(); };
    $('method-select').onchange = (e) => { settings.method = e.target.value; save(true); };
    $('asr-select').onchange = (e) => { settings.asr = e.target.value; save(true); };
    $('volume').oninput = (e) => { settings.volume = +e.target.value; player.volume = settings.volume; save(); };
    $('notify').onchange = async (e) => {
      settings.notify = e.target.checked && (await requestNotify());
      e.target.checked = settings.notify;
      save();
    };

    $('city-select').onchange = (e) => {
      const c = CITIES[+e.target.value];
      if (c) setLocation({ lat: c.lat, lng: c.lng, name: c.name, tz: c.tz }, c.method);
    };
    $('save-coords').onclick = () => {
      const lat = parseFloat($('lat').value), lng = parseFloat($('lng').value);
      if (!(Math.abs(lat) <= 90 && Math.abs(lng) <= 180)) { alert('إحداثيات غير صحيحة'); return; }
      setLocation({ lat, lng, name: `${lat.toFixed(3)}، ${lng.toFixed(3)}`, tz: deviceTz });
    };
    $('use-gps').onclick = useGps;

    $('open-settings').onclick = () => { renderSettings(); renderAudioManager(); $('settings').showModal(); };
    $('settings').addEventListener('close', renderMuezzinSource);
    $('unlock-audio').onclick = unlockAudio;
    $('preview').onclick = () => {
      if (!audioUnlocked) unlockAudio();
      $('preview').hidden = true; $('stop').hidden = false;
      playMuezzin(settings.muezzin);
    };
    $('stop').onclick = stopAudio;
    $('azan-stop').onclick = stopAudio;
  }

  function start() {
    bind();
    renderSettings();
    renderMuezzinSource();
    if (settings.location) {
      $('location-name').textContent = settings.location.name;
    } else {
      // موقع افتراضي حتى يحدد المستخدم موقعه
      const c = CITIES[0];
      settings.location = { lat: c.lat, lng: c.lng, name: c.name, tz: c.tz };
      $('location-name').textContent = c.name;
      useGps();
    }
    tick();
    setInterval(tick, 1000);
    document.addEventListener('visibilitychange', tick);

    if ('serviceWorker' in navigator && location.protocol !== 'file:') {
      navigator.serviceWorker.register('sw.js').catch(() => {});
    }
  }

  start();
})();
