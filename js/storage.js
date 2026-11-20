/* حفظ الإعدادات (localStorage) وملفات الأذان المرفوعة (IndexedDB) */
(function (root) {
  'use strict';

  const SETTINGS_KEY = 'prayer-times:settings';
  const DEFAULTS = {
    location: null,            // { lat, lng, name, tz }
    method: 'egypt',
    asr: 'standard',
    muezzin: 'abdulbasit',
    fajrMuezzin: 'same',
    volume: 0.9,
    enabled: { fajr: true, dhuhr: true, asr: true, maghrib: true, isha: true },
    adjust: { fajr: 0, sunrise: 0, dhuhr: 0, asr: 0, maghrib: 0, isha: 0 },
    customUrls: {},            // { [muezzinId]: url }
    notify: false,
  };

  function loadSettings() {
    try {
      const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}');
      return {
        ...DEFAULTS, ...saved,
        enabled: { ...DEFAULTS.enabled, ...(saved.enabled || {}) },
        adjust: { ...DEFAULTS.adjust, ...(saved.adjust || {}) },
        customUrls: { ...(saved.customUrls || {}) },
      };
    } catch (e) {
      return structuredClone(DEFAULTS);
    }
  }

  function saveSettings(s) {
    try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(s)); } catch (e) { /* وضع التصفح الخاص */ }
  }

  // ---- IndexedDB لملفات الصوت ----
  let dbPromise = null;
  function db() {
    if (!dbPromise) {
      dbPromise = new Promise((resolve, reject) => {
        const req = indexedDB.open('prayer-times-audio', 1);
        req.onupgradeneeded = () => req.result.createObjectStore('audio');
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
    }
    return dbPromise;
  }
  async function tx(mode, fn) {
    const d = await db();
    return new Promise((resolve, reject) => {
      const t = d.transaction('audio', mode);
      const req = fn(t.objectStore('audio'));
      t.oncomplete = () => resolve(req && req.result);
      t.onerror = () => reject(t.error);
    });
  }
  const putAudio = (id, blob) => tx('readwrite', (s) => s.put(blob, id));
  const getAudio = (id) => tx('readonly', (s) => s.get(id)).catch(() => null);
  const deleteAudio = (id) => tx('readwrite', (s) => s.delete(id));
  const listAudioIds = () => tx('readonly', (s) => s.getAllKeys()).catch(() => []);

  root.Store = { loadSettings, saveSettings, putAudio, getAudio, deleteAudio, listAudioIds, DEFAULTS };
})(self);
