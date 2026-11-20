/* يعمل التطبيق بدون إنترنت بعد أول فتح */
const CACHE = 'prayer-times-v1';
const SHELL = [
  './', 'index.html', 'css/style.css', 'js/praytimes.js', 'js/muezzins.js',
  'js/storage.js', 'js/app.js', 'manifest.webmanifest', 'icon.svg',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  // ملفات الصوت والروابط الخارجية تُجلب مباشرة (طلبات Range للصوت لا تُخزَّن)
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.includes('/audio/')) return;
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const copy = res.clone();
        if (res.ok) caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request).then((r) => r || caches.match('index.html'))),
  );
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: 'window' }).then((list) => (list[0] ? list[0].focus() : self.clients.openWindow('./'))),
  );
});
