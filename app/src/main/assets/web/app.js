/* TV Home – Startseite für Android TV mit Home-Assistant-Anbindung. */
(function () {
  'use strict';

  // ------------------------------------------------------------------ Einstellungen
  const DEF = {
    layout: 'dock', material: 'clear', focus: 'glass', iconStyle: 'white', labels: 'on',
    clock: '24', date: 'on', textSize: 'normal',
    bgMode: 'fixed', bgId: '01', bgInterval: 15, dim: 10, kenburns: 'on',
    artAfter: 5, artClock: 'big',
    smart: 'on', camPreview: 'live', appliances: 'on',
    popupEnabled: true, popupPos: 'tr', popupSize: 'm', popupSeconds: 30, popupCam: 'live',
    favorites: [],
    lightEntity: '', fanEntity: '', cameraEntity: '', doorbellEntity: '',
    dishEntity: '', dishStatusEntity: '', ovenEntity: '', ovenStatusEntity: '',
    lightName: 'Licht', fanName: 'Ventilator', cameraName: 'Haustür'
  };
  let cfg = {};
  function loadCfg() {
    let raw = {};
    try { raw = JSON.parse(Android.getConfig() || '{}'); } catch (e) {}
    cfg = Object.assign({}, DEF, raw);
  }
  function saveCfg(patch) {
    Object.assign(cfg, patch);
    try { Android.saveConfig(JSON.stringify(patch)); } catch (e) {}
  }

  const SETTINGS = [
    { section: 'Darstellung' },
    { key: 'layout', label: 'Layout', opts: [['dock', 'Dock'], ['list', 'Liste']] },
    { key: 'material', label: 'Material', opts: [['clear', 'Klar getönt'], ['frost', 'Milchglas'], ['dark', 'Dunkel getönt'], ['none', 'Ohne']] },
    { key: 'focus', label: 'Fokus', opts: [['glass', 'Glas'], ['glow', 'Leuchten'], ['lift', 'Vergrößern'], ['outline', 'Rahmen']] },
    { key: 'iconStyle', label: 'App-Symbole', opts: [['white', 'Weiß'], ['color', 'Farbig']] },
    { key: 'labels', label: 'App-Namen im Dock', opts: [['on', 'Anzeigen'], ['off', 'Ausblenden']] },
    { key: 'clock', label: 'Uhr', opts: [['24', '24 Std.'], ['12', '12 Std.'], ['off', 'Aus']] },
    { key: 'date', label: 'Datum', opts: [['on', 'An'], ['off', 'Aus']] },
    { key: 'textSize', label: 'Schriftgröße', opts: [['normal', 'Normal'], ['big', 'Groß']] },
    { section: 'Hintergrund' },
    { act: 'bgpicker', label: 'Hintergrundbild wählen', icon: 'image', value: () => currentBgName() },
    { key: 'bgMode', label: 'Modus', opts: [['fixed', 'Festes Bild'], ['rotate', 'Wechselnd (eingebaute)'], ['custom', 'Wechselnd (eigene Fotos)'], ['bing', 'Bing Bild des Tages']] },
    { key: 'bgInterval', label: 'Wechsel alle', opts: [[1, '1 Min.'], [5, '5 Min.'], [15, '15 Min.'], [60, '1 Std.'], [240, '4 Std.']] },
    { key: 'dim', label: 'Abdunkeln', opts: [[0, 'Aus'], [10, '10 %'], [20, '20 %'], [30, '30 %'], [45, '45 %'], [60, '60 %']] },
    { key: 'kenburns', label: 'Langsamer Zoom', opts: [['on', 'An'], ['off', 'Aus']] },
    { section: 'Art Mode' },
    { key: 'artAfter', label: 'Automatisch nach', opts: [[0, 'Nie'], [1, '1 Min.'], [3, '3 Min.'], [5, '5 Min.'], [10, '10 Min.'], [30, '30 Min.']] },
    { key: 'artClock', label: 'Uhr im Art Mode', opts: [['big', 'Groß'], ['small', 'Klein'], ['off', 'Aus']] },
    { section: 'Smart Home' },
    { key: 'smart', label: 'Smart-Home-Leiste', opts: [['on', 'Anzeigen'], ['off', 'Ausblenden']] },
    { key: 'camPreview', label: 'Kamera-Vorschau', opts: [['live', 'Live'], ['still', 'Standbild (10 s)'], ['off', 'Aus']] },
    { key: 'appliances', label: 'Restlaufzeit Geräte', opts: [['on', 'Wenn aktiv'], ['off', 'Aus']] },
    { section: 'Klingel-Popup' },
    { key: 'popupEnabled', label: 'Popup beim Klingeln', opts: [[true, 'An'], [false, 'Aus']] },
    { key: 'popupPos', label: 'Position', opts: [['tr', 'Oben rechts'], ['tl', 'Oben links'], ['br', 'Unten rechts'], ['bl', 'Unten links']] },
    { key: 'popupSize', label: 'Größe', opts: [['s', 'Klein'], ['m', 'Mittel'], ['l', 'Groß']] },
    { key: 'popupSeconds', label: 'Anzeigedauer', opts: [[15, '15 Sek.'], [30, '30 Sek.'], [60, '1 Min.'], [120, '2 Min.']] },
    { key: 'popupCam', label: 'Kamerabild im Popup', opts: [['live', 'Live'], ['still', 'Standbild']] },
    { act: 'testpopup', label: 'Popup testen', icon: 'bell', value: () => (Android.canOverlay() ? '' : 'Berechtigung fehlt') },
    { section: 'Einrichtung' },
    { act: 'setup', label: 'Am Handy einrichten', icon: 'phone', value: () => 'HA: ' + HA.status },
    { act: 'update', label: 'Nach Update suchen', icon: 'update', value: () => 'Version ' + Android.version() },
    { act: 'android', label: 'Android-Einstellungen', icon: 'android', value: () => '' }
  ];

  // ------------------------------------------------------------------ Hilfen
  const $ = (s, r) => (r || document).querySelector(s);
  function h(tag, attrs, kids) {
    const el = document.createElement(tag);
    if (attrs) for (const k in attrs) {
      const v = attrs[k];
      if (v === undefined || v === null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'html') el.innerHTML = v;
      else if (k === 'text') el.textContent = v;
      else if (k === 'style') el.setAttribute('style', v);
      else if (k.startsWith('_')) el[k] = v;
      else el.setAttribute(k, v === true ? '' : v);
    }
    (kids || []).forEach((c) => { if (c) el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return el;
  }
  const icon = (name, cls) => h('span', { class: cls || 'ic', html: ICON[name] || '' });
  const pad = (n) => String(n).padStart(2, '0');
  function fmtTime(d) {
    if (cfg.clock === '12') { let hh = d.getHours() % 12 || 12; return hh + ':' + pad(d.getMinutes()); }
    return d.getHours() + ':' + pad(d.getMinutes());
  }
  const fmtDate = (d) => d.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' });
  let toastTimer;
  function toast(msg) {
    const t = $('#toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2600);
  }
  const later = (fn) => setTimeout(fn, 30);

  // ------------------------------------------------------------------ Darstellung anwenden
  function applyCfg() {
    const b = document.body;
    const keep = Array.from(b.classList).filter((c) => c === 'art' || c.startsWith('artclock-'));
    b.className = '';
    keep.forEach((c) => b.classList.add(c));
    b.classList.add('layout-' + cfg.layout, 'mat-' + cfg.material, 'focus-' + cfg.focus, 'icons-' + cfg.iconStyle);
    if (cfg.labels === 'off') b.classList.add('labels-off');
    if (cfg.clock === 'off') b.classList.add('clock-off');
    if (cfg.date === 'off') b.classList.add('date-off');
    if (cfg.textSize === 'big') b.classList.add('big');
    if (cfg.kenburns === 'on') b.classList.add('kenburns');
    if (cfg.smart === 'off') b.classList.add('smart-off');
    b.classList.remove('artclock-big', 'artclock-small');
    if (cfg.artClock !== 'off') b.classList.add('artclock-' + cfg.artClock);
    document.documentElement.style.setProperty('--dim', (Number(cfg.dim) || 0) / 100);
    tickClock();
  }

  // ------------------------------------------------------------------ Uhr
  function tickClock() {
    const d = new Date();
    $('#clock').textContent = fmtTime(d);
    $('#date').textContent = fmtDate(d);
    $('#artclock-time').textContent = fmtTime(d);
    $('#artclock-date').textContent = fmtDate(d);
  }

  // ------------------------------------------------------------------ Apps
  let apps = [];
  const iconCache = {};
  const PREFERRED = ['stremio', 'netflix', 'disney', 'youtube', 'apple tv', 'prime video', 'plex', 'mediathek', 'zdf', 'spotify', 'kodi', 'waipu', 'joyn', 'magenta', 'dazn', 'sky', 'wow'];
  function loadApps() { try { apps = JSON.parse(Android.getApps() || '[]'); } catch (e) { apps = []; } }
  function favApps() {
    const byPkg = {}; apps.forEach((a) => (byPkg[a.pkg] = a));
    if (cfg.favorites && cfg.favorites.length) return cfg.favorites.map((p) => byPkg[p]).filter(Boolean);
    const score = (a) => { const l = a.label.toLowerCase(); const i = PREFERRED.findIndex((p) => l.includes(p)); return i < 0 ? 99 : i; };
    return apps.filter((a) => score(a) < 99).sort((a, b) => score(a) - score(b)).slice(0, 10)
      .concat(apps.filter((a) => score(a) === 99)).slice(0, 10);
  }
  function appIcon(a) {
    if (!(a.pkg in iconCache)) { try { iconCache[a.pkg] = Android.getIcon(a.pkg) || ''; } catch (e) { iconCache[a.pkg] = ''; } }
    const src = iconCache[a.pkg];
    if (src) return h('img', { src });
    return h('div', { class: 'mono', text: a.label.replace(/[^A-Za-z0-9ÄÖÜäöü]/g, '').slice(0, 1).toUpperCase() });
  }
  function launch(a) {
    if (!Android.launch(a.pkg)) toast(a.label + ' lässt sich nicht starten');
  }
  function ensureFavs() { if (!cfg.favorites || !cfg.favorites.length) saveCfg({ favorites: favApps().map((a) => a.pkg) }); }
  function moveFav(pkg, delta) {
    ensureFavs();
    const f = cfg.favorites.slice(); const i = f.indexOf(pkg); const j = i + delta;
    if (i < 0 || j < 0 || j >= f.length) return;
    f.splice(i, 1); f.splice(j, 0, pkg); saveCfg({ favorites: f }); renderApps(pkg);
  }
  function toggleFav(a) {
    ensureFavs();
    const f = cfg.favorites.slice(); const i = f.indexOf(a.pkg);
    if (i >= 0) { f.splice(i, 1); toast(a.label + ' aus der Leiste entfernt'); } else { f.push(a.pkg); toast(a.label + ' zur Leiste hinzugefügt'); }
    saveCfg({ favorites: f }); renderApps();
  }
  function appMenu(a) {
    const vertical = cfg.layout === 'list';
    menu(a.label, [
      { label: vertical ? 'Nach oben verschieben' : 'Nach links verschieben', act: () => moveFav(a.pkg, -1) },
      { label: vertical ? 'Nach unten verschieben' : 'Nach rechts verschieben', act: () => moveFav(a.pkg, 1) },
      { label: 'Aus der Leiste entfernen', act: () => toggleFav(a) }
    ]);
  }

  function renderApps(focusPkg) {
    const favs = favApps();
    const dock = $('#docktrack'); dock.innerHTML = '';
    const list = $('#listtrack'); list.innerHTML = '';
    favs.forEach((a) => {
      const act = (long) => (long ? appMenu(a) : launch(a));
      dock.appendChild(h('div', { class: 'app', 'data-f': true, 'data-pkg': a.pkg, _act: act },
        [h('div', { class: 'ico' }, [appIcon(a)]), h('div', { class: 'lbl', text: a.label })]));
      list.appendChild(h('div', { class: 'lrow' }, [h('div', { class: 'litem', 'data-f': true, 'data-pkg': a.pkg, _act: act },
        [h('div', { class: 'ico' }, [appIcon(a)]), h('span', { text: a.label })])]));
    });
    if (focusPkg) {
      const sel = (cfg.layout === 'list' ? '#listtrack' : '#docktrack') + ' [data-pkg="' + CSS.escape(focusPkg) + '"]';
      const el = $(sel); if (el) setFocus(el);
    }
  }

  // ------------------------------------------------------------------ Fokus & Navigation
  let focused = null;
  const modals = [];
  const scope = () => (modals.length ? modals[modals.length - 1].el : $('#ui'));
  function visible(el) { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; }
  function focusables() { return Array.from(scope().querySelectorAll('[data-f]')).filter(visible); }
  function setFocus(el) {
    if (!el) return;
    if (focused) focused.classList.remove('focused');
    focused = el; el.classList.add('focused');
    scrollIntoView(el);
  }
  function defaultFocus() {
    const s = scope();
    if (s.id === 'ui') {
      const first = $(cfg.layout === 'list' ? '#listtrack [data-f]' : '#docktrack [data-f]');
      if (first) return first;
    }
    const pref = s.querySelector('[data-f].current') || s.querySelector('[data-f]');
    return pref && visible(pref) ? pref : focusables()[0];
  }
  function ensureFocus() { if (!focused || !document.body.contains(focused) || !scope().contains(focused) || !visible(focused)) setFocus(defaultFocus()); }

  let dockOffset = 0, listOffset = 0;
  function scrollIntoView(el) {
    if (el.closest('#docktrack')) {
      const view = $('#dock').clientWidth - 16;
      const left = el.offsetLeft, right = left + el.offsetWidth;
      if (right - dockOffset > view) dockOffset = right - view;
      if (left - dockOffset < 0) dockOffset = left;
      $('#docktrack').style.transform = 'translateX(' + -dockOffset + 'px)';
    } else if (el.closest('#listtrack')) {
      const view = $('#list').clientHeight;
      const top = el.offsetTop, bottom = top + el.offsetHeight;
      if (bottom - listOffset > view * 0.72) listOffset = bottom - view * 0.72;
      if (top - listOffset < 0) listOffset = top;
      listOffset = Math.max(0, listOffset);
      $('#listtrack').style.transform = 'translateY(' + -listOffset + 'px)';
    } else {
      const sc = el.closest('.scroll');
      if (!sc) return;
      const tr = sc.firstElementChild;
      let off = tr._off || 0;
      const top = el.offsetTop, bottom = top + el.offsetHeight, view = sc.clientHeight;
      if (bottom - off > view - 10) off = bottom - view + 10;
      if (top - off < 10) off = Math.max(0, top - 10);
      tr._off = off; tr.style.transform = 'translateY(' + -off + 'px)';
    }
  }

  function move(dir) {
    ensureFocus();
    if (!focused) return;
    if ((dir === 'left' || dir === 'right') && focused._lr) { focused._lr(dir === 'left' ? -1 : 1); return; }
    const c = focused.getBoundingClientRect();
    const cx = c.left + c.width / 2, cy = c.top + c.height / 2;
    let best = null, bestScore = Infinity;
    focusables().forEach((el) => {
      if (el === focused) return;
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2, y = r.top + r.height / 2;
      let primary, orth, overlap;
      if (dir === 'right') { if (r.left < c.right - c.width * 0.3 || x <= cx) return; primary = r.left - c.right; orth = Math.abs(y - cy); overlap = r.top < c.bottom && r.bottom > c.top; }
      if (dir === 'left') { if (r.right > c.left + c.width * 0.3 || x >= cx) return; primary = c.left - r.right; orth = Math.abs(y - cy); overlap = r.top < c.bottom && r.bottom > c.top; }
      if (dir === 'down') { if (r.top < c.bottom - c.height * 0.3 || y <= cy) return; primary = r.top - c.bottom; orth = Math.abs(x - cx); overlap = r.left < c.right && r.right > c.left; }
      if (dir === 'up') { if (r.bottom > c.top + c.height * 0.3 || y >= cy) return; primary = c.top - r.bottom; orth = Math.abs(x - cx); overlap = r.left < c.right && r.right > c.left; }
      const score = Math.max(primary, 0) + orth * (overlap ? 0.5 : 3);
      if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) setFocus(best);
  }

  // ------------------------------------------------------------------ Tasten
  let lastKey = Date.now();
  let enterTimer = null, longFired = false, swallow = false;
  function activate(long) {
    ensureFocus();
    if (focused && focused._act) focused._act(!!long);
  }
  document.addEventListener('keydown', (e) => {
    lastKey = Date.now();
    const k = e.key;
    const isEnter = k === 'Enter' || e.keyCode === 13 || e.keyCode === 23 || k === ' ';
    if (document.body.classList.contains('art')) { exitArt(); swallow = true; e.preventDefault(); return; }
    if (swallow && e.repeat) { e.preventDefault(); return; }
    swallow = false;
    if (k === 'ArrowLeft') move('left');
    else if (k === 'ArrowRight') move('right');
    else if (k === 'ArrowUp') move('up');
    else if (k === 'ArrowDown') move('down');
    else if (isEnter) {
      e.preventDefault();
      if (e.repeat) return;
      longFired = false;
      clearTimeout(enterTimer);
      enterTimer = setTimeout(() => { longFired = true; enterTimer = null; activate(true); }, 650);
      return;
    } else if (k === 'Escape' || k === 'Backspace' || k === 'GoBack' || k === 'BrowserBack') { back(); }
    else return;
    e.preventDefault();
  });
  document.addEventListener('keyup', (e) => {
    const isEnter = e.key === 'Enter' || e.keyCode === 13 || e.keyCode === 23 || e.key === ' ';
    if (!isEnter) return;
    e.preventDefault();
    if (swallow) { swallow = false; return; }
    if (enterTimer) { clearTimeout(enterTimer); enterTimer = null; if (!longFired) activate(false); }
  });

  function back() {
    lastKey = Date.now();
    if (document.body.classList.contains('art')) { exitArt(); return; }
    if (modals.length) { closeModal(); return; }
    setFocus(defaultFocus());
  }
  window.tvBack = back;
  window.tvHome = () => {
    while (modals.length) closeModal();
    exitArt();
    dockOffset = 0; listOffset = 0;
    setFocus(defaultFocus());
  };

  // ------------------------------------------------------------------ Fenster
  function openModal(content, opts) {
    opts = opts || {};
    const el = h('div', { class: 'modal' + (opts.side ? ' side' : '') }, [content]);
    $('#modals').appendChild(el);
    modals.push({ el, prev: focused, onClose: opts.onClose, refresh: opts.refresh });
    focused && focused.classList.remove('focused');
    focused = null;
    later(() => setFocus(opts.focus || defaultFocus()));
    return el;
  }
  function closeModal() {
    const m = modals.pop(); if (!m) return;
    m.el.remove();
    if (m.onClose) m.onClose();
    focused = null;
    if (m.prev && document.body.contains(m.prev)) setFocus(m.prev); else setFocus(defaultFocus());
  }
  function refreshModals() { modals.forEach((m) => m.refresh && m.refresh()); }

  function menu(title, items) {
    const rows = items.map((it) => h('div', { class: 'row', 'data-f': true, _act: () => { closeModal(); it.act(); } }, [h('span', { text: it.label })]));
    openModal(h('div', { class: 'sheet menu' }, [h('h2', { text: title })].concat(rows)));
  }

  // ------------------------------------------------------------------ Einstellungen
  function optLabel(s) {
    const o = s.opts.find((x) => String(x[0]) === String(cfg[s.key]));
    return o ? o[1] : s.opts[0][1];
  }
  function openSettings() {
    const track = h('div', { class: 'scrolltrack' });
    const rows = [];
    SETTINGS.forEach((s) => {
      if (s.section) { track.appendChild(h('div', { class: 'section', text: s.section })); return; }
      const val = h('span', { class: 'txt' });
      const row = h('div', { class: 'row', 'data-f': true }, [
        h('div', { class: 'lft' }, [s.icon ? icon(s.icon) : null, h('span', { text: s.label })]),
        h('div', { class: 'val' }, s.opts ? [icon('left', 'arr'), val, icon('right', 'arr')] : [val])
      ]);
      const upd = () => { val.textContent = s.opts ? optLabel(s) : (s.value ? s.value() : ''); };
      if (s.opts) {
        const step = (d) => {
          let i = s.opts.findIndex((x) => String(x[0]) === String(cfg[s.key]));
          if (i < 0) i = 0;
          i = (i + d + s.opts.length) % s.opts.length;
          const p = {}; p[s.key] = s.opts[i][0]; saveCfg(p); onCfgChanged(s.key);
          rows.forEach((r) => r._upd());
        };
        row._lr = step;
        row._act = () => step(1);
      } else {
        row._act = () => settingsAction(s.act);
      }
      row._upd = upd; upd(); rows.push(row);
      track.appendChild(row);
    });
    const sheet = h('div', { class: 'sheet' }, [h('h2', {}, [icon('gear'), 'Einstellungen']), h('div', { class: 'scroll' }, [track])]);
    openModal(sheet, { side: true, refresh: () => rows.forEach((r) => r._upd()) });
  }
  function onCfgChanged(key) {
    applyCfg();
    if (key === 'layout') { dockOffset = 0; listOffset = 0; renderApps(); }
    if (key === 'iconStyle' || key === 'labels' || key === 'textSize') renderApps();
    if (key && key.startsWith('bg')) { bgIndex = 0; loadBackgroundLists(); startBg(true); }
    if (key === 'camPreview' || key === 'smart' || key === 'appliances') { renderSmart(true); renderStatus(); }
  }
  function settingsAction(a) {
    if (a === 'bgpicker') openBgPicker();
    else if (a === 'testpopup') {
      if (!Android.canOverlay()) toast('Popup-Berechtigung fehlt – wird bei der Installation per Befehl gesetzt');
      else { Android.testPopup(); toast('Test-Popup ausgelöst'); }
    } else if (a === 'setup') openSetup();
    else if (a === 'update') checkUpdate();
    else if (a === 'android') Android.openAndroidSettings();
  }

  function openSetup() {
    const url = Android.setupUrl();
    const qr = Android.qr(url);
    const pin = (url.match(/pin=(\d+)/) || [])[1] || '';
    openModal(h('div', { class: 'sheet', style: 'width:58vw' }, [
      h('h2', {}, [icon('phone'), 'Am Handy einrichten']),
      h('div', { class: 'qr' }, [
        qr ? h('img', { src: qr }) : null,
        h('div', { class: 'txt' }, [
          h('div', { html: '1. Handy ins <b>gleiche WLAN</b> wie den Shield' }),
          h('div', { html: '2. QR-Code mit der Kamera scannen' }),
          h('div', { html: '3. Home Assistant verbinden, Geräte auswählen, Apps und Fotos verwalten' }),
          h('div', { class: 'url', text: url.replace(/\?pin=.*/, '') }),
          h('div', { html: 'PIN: <b>' + pin + '</b> · Home Assistant: <b>' + HA.status + '</b>', style: 'margin-top:.6vw;opacity:.8' })
        ])
      ]),
      h('div', { class: 'btn', 'data-f': true, style: 'margin-top:1.4vw;align-self:flex-start', _act: closeModal }, ['Fertig'])
    ]));
  }

  function checkUpdate() {
    toast('Suche nach Updates …');
    setTimeout(() => {
      let r = {}; try { r = JSON.parse(Android.checkUpdate()); } catch (e) {}
      if (r.error) { toast(r.error); return; }
      if (!r.available) { toast('Du hast die neueste Version (' + Android.version() + ')'); return; }
      menu('Neue Version ' + r.latest + ' verfügbar', [
        { label: 'Jetzt installieren', act: () => { toast('Lade herunter …'); setTimeout(() => { const err = Android.installUpdate(r.url); if (err) toast(err); }, 50); } },
        { label: 'Später', act: () => {} }
      ]);
    }, 60);
  }

  // ------------------------------------------------------------------ Alle Apps
  function openAllApps() {
    loadApps();
    const favs = new Set(favApps().map((a) => a.pkg));
    const grid = h('div', { class: 'grid', style: 'grid-template-columns:repeat(6,1fr)' });
    apps.forEach((a) => {
      const tile = h('div', { class: 'tile', 'data-f': true, _act: (long) => {
        if (long) { toggleFav(a); const st = tile.querySelector('.fav'); if (st) st.remove(); else tile.appendChild(icon('star', 'fav')); }
        else launch(a);
      } }, [h('div', { class: 'ico' }, [appIcon(a)]), h('div', { text: a.label }), favs.has(a.pkg) ? icon('star', 'fav') : null]);
      grid.appendChild(tile);
    });
    openModal(h('div', { class: 'sheet', style: 'width:84vw;height:84vh' }, [
      h('h2', {}, [icon('grid'), 'Alle Apps']),
      h('div', { class: 'sub', text: 'OK gedrückt halten: zur Leiste hinzufügen oder entfernen' }),
      h('div', { class: 'scroll' }, [h('div', { class: 'scrolltrack' }, [grid])])
    ]));
  }

  // ------------------------------------------------------------------ Hintergrund
  const GRADIENTS = [
    'linear-gradient(160deg,#1d3a4a,#3f6e73 45%,#c9a76b)',
    'linear-gradient(160deg,#141e30,#243b55)',
    'linear-gradient(160deg,#2b1d3a,#6b3e5c 50%,#e09b6b)'
  ];
  let bgBuiltin = [], bgCustom = [], bgBing = [], bgIndex = 0, bgTimer = null, bingLoaded = 0, bgFront = 'A', bgCurrentUrl = '';
  function loadBackgroundLists() {
    try { bgBuiltin = JSON.parse(Android.builtinBackgrounds() || '[]'); } catch (e) { bgBuiltin = []; }
    try { bgCustom = JSON.parse(Android.customBackgrounds() || '[]'); } catch (e) { bgCustom = []; }
  }
  function loadBing(cb) {
    if (Date.now() - bingLoaded < 3 * 3600 * 1000 && bgBing.length) { cb && cb(); return; }
    setTimeout(() => {
      try { bgBing = JSON.parse(Android.bingBackgrounds() || '[]'); } catch (e) { bgBing = []; }
      bingLoaded = Date.now(); cb && cb();
    }, 50);
  }
  const allBgs = () => bgBuiltin.concat(bgCustom, bgBing);
  function activeList() {
    if (cfg.bgMode === 'rotate') return bgBuiltin;
    if (cfg.bgMode === 'custom') return bgCustom.length ? bgCustom : bgBuiltin;
    if (cfg.bgMode === 'bing') return bgBing.length ? bgBing : bgBuiltin;
    const f = allBgs().find((b) => b.id === cfg.bgId) || bgBuiltin[0];
    return f ? [f] : [];
  }
  function currentBg() { const l = activeList(); return l.length ? l[bgIndex % l.length] : null; }
  function currentBgName() { const b = currentBg(); return b ? b.name : 'Farbverlauf'; }
  function showBg(b) {
    const url = b ? b.url : null;
    if (url && url === bgCurrentUrl) return;
    const next = bgFront === 'A' ? $('#bgB') : $('#bgA');
    const cur = bgFront === 'A' ? $('#bgA') : $('#bgB');
    const swap = () => {
      next.style.backgroundImage = url ? 'url("' + url + '")' : GRADIENTS[0];
      next.classList.add('show'); cur.classList.remove('show');
      bgFront = bgFront === 'A' ? 'B' : 'A'; bgCurrentUrl = url || '';
      $('#artclock-info').textContent = b && b.info ? b.info : '';
    };
    if (!url) { swap(); return; }
    const img = new Image();
    img.onload = swap;
    img.onerror = () => { if (!bgCurrentUrl) { bgCurrentUrl = ''; next.style.backgroundImage = GRADIENTS[0]; next.classList.add('show'); } };
    img.src = url;
  }
  function startBg(immediate) {
    clearInterval(bgTimer);
    const go = () => showBg(currentBg());
    if (cfg.bgMode === 'bing') loadBing(go); else go();
    if (cfg.bgMode !== 'fixed') {
      bgTimer = setInterval(() => {
        const l = activeList(); if (l.length < 2) return;
        bgIndex = (bgIndex + 1) % l.length; showBg(currentBg());
      }, Math.max(1, Number(cfg.bgInterval) || 15) * 60000);
    }
  }
  function openBgPicker() {
    loadBackgroundLists();
    const track = h('div', { class: 'scrolltrack' });
    const sheet = h('div', { class: 'sheet', style: 'width:84vw;height:86vh' }, [
      h('h2', {}, [icon('image'), 'Hintergrundbild wählen']),
      h('div', { class: 'sub', text: 'Auswählen setzt den Modus auf „Festes Bild“. Eigene Fotos lädst du über „Am Handy einrichten“ hoch.' }),
      h('div', { class: 'scroll' }, [track])
    ]);
    const build = () => {
      track.innerHTML = '';
      const groups = [['Eingebaut', bgBuiltin], ['Eigene Fotos', bgCustom], ['Bing Bild des Tages', bgBing]];
      groups.forEach(([title, list]) => {
        if (!list.length) return;
        track.appendChild(h('div', { class: 'section', text: title }));
        const grid = h('div', { class: 'grid', style: 'grid-template-columns:repeat(5,1fr)' });
        list.forEach((b) => {
          const cur = cfg.bgMode === 'fixed' && cfg.bgId === b.id;
          grid.appendChild(h('div', { class: 'thumb' + (cur ? ' current' : ''), 'data-f': true, _act: () => {
            saveCfg({ bgMode: 'fixed', bgId: b.id }); bgIndex = 0; startBg(true);
            track.querySelectorAll('.ok').forEach((x) => x.remove());
            this_ok(grid, b.id);
            toast('Hintergrund: ' + b.name);
            refreshModals();
          }, 'data-bg': b.id }, [
            h('div', { class: 'im', style: 'background-image:url("' + (b.thumb || b.url) + '")' }, cur ? [icon('check', 'ok')] : []),
            h('div', { class: 'nm', text: b.name })
          ]));
        });
        track.appendChild(grid);
      });
    };
    const this_ok = (grid, id) => { const t = grid.querySelector('[data-bg="' + CSS.escape(id) + '"] .im'); if (t) t.appendChild(icon('check', 'ok')); };
    build();
    openModal(sheet);
    loadBing(() => { const f = focused; build(); if (modals.length && f && !document.body.contains(f)) setFocus(defaultFocus()); });
  }

  // ------------------------------------------------------------------ Art Mode
  function enterArt() {
    if (document.body.classList.contains('art')) return;
    while (modals.length) closeModal();
    document.body.classList.add('art');
  }
  function exitArt() { document.body.classList.remove('art'); lastKey = Date.now(); }
  let paused = false;
  setInterval(() => {
    const after = Number(cfg.artAfter) || 0;
    if (!paused && after > 0 && !modals.length && Date.now() - lastKey > after * 60000) enterArt();
  }, 10000);

  // ------------------------------------------------------------------ Smart Home
  const OFF_STATES = ['off', 'idle', 'ready', 'inactive', 'finished', 'unavailable', 'unknown', 'standby', 'aus', 'bereit', 'fertig', 'none', '', 'stopped', 'paused_off'];
  function remaining(id) {
    const s = HA.get(id); if (!s) return null;
    const st = String(s.state || '');
    if (['unavailable', 'unknown', '', 'none', 'None'].includes(st)) return null;
    const a = s.attributes || {};
    if (a.device_class === 'timestamp' || /^\d{4}-\d\d-\d\dT/.test(st)) { const t = Date.parse(st); return isNaN(t) ? null : (t - Date.now()) / 60000; }
    const m = st.match(/^(\d+):(\d\d)(?::(\d\d))?$/);
    if (m) return Number(m[1]) * 60 + Number(m[2]) + (m[3] ? Number(m[3]) / 60 : 0);
    const v = parseFloat(st); if (isNaN(v)) return null;
    const u = (a.unit_of_measurement || 'min').toLowerCase();
    if (u === 'h') return v * 60; if (u === 's') return v / 60; if (u === 'd') return v * 1440;
    return v;
  }
  function running(id, statusId, mins) {
    if (statusId) { const s = HA.get(statusId); if (s) return !OFF_STATES.includes(String(s.state).toLowerCase()) && !/(finish|ready|inactive|off|aus|beendet)/i.test(s.state); }
    return mins !== null && mins > 0.5 && mins < 24 * 60;
  }
  function fmtDur(m) { m = Math.max(0, Math.round(m)); return Math.floor(m / 60) + ':' + pad(m % 60); }

  function renderStatus() {
    const box = $('#status'); box.innerHTML = '';
    if (cfg.appliances === 'off') return;
    [['dishEntity', 'dishStatusEntity', 'dishwasher', 'Spülmaschine'], ['ovenEntity', 'ovenStatusEntity', 'oven', 'Backofen']].forEach(([k, sk, ic, name]) => {
      if (!cfg[k]) return;
      const mins = remaining(cfg[k]);
      if (!running(cfg[k], cfg[sk], mins)) return;
      const kids = [icon(ic), h('span', { text: name })];
      if (mins !== null && mins > 0) {
        kids.push(h('b', { text: fmtDur(mins) }));
        const end = new Date(Date.now() + mins * 60000);
        kids.push(h('span', { class: 'sub', text: '· fertig ' + fmtTime(end) }));
      } else kids.push(h('span', { class: 'sub', text: 'läuft' }));
      box.appendChild(h('div', { class: 'chip glass' }, kids));
    });
  }

  let smartEls = null, smartKey = '', camUrlSet = '', stillTimer = null;
  const domainOf = (id) => (id || '').split('.')[0];
  const nameOf = (id, fallback) => { const s = HA.get(id); return (s && s.attributes.friendly_name) || fallback; };
  function lightInfo() {
    const s = HA.get(cfg.lightEntity); if (!s) return 'nicht verbunden';
    if (s.state !== 'on') return 'Aus';
    const b = s.attributes.brightness; return b ? 'An · ' + Math.round(b / 2.55) + ' %' : 'An';
  }
  function fanInfo() {
    const s = HA.get(cfg.fanEntity); if (!s) return 'nicht verbunden';
    if (s.state !== 'on') return 'Aus';
    const p = s.attributes.percentage, st = s.attributes.percentage_step;
    if (p && st && st >= 10) return 'An · Stufe ' + Math.round(p / st);
    if (p) return 'An · ' + Math.round(p) + ' %';
    return 'An';
  }
  function renderSmart(force) {
    const box = $('#smart');
    const key = [cfg.cameraEntity, cfg.lightEntity, cfg.fanEntity, cfg.camPreview].join('|');
    if (force || key !== smartKey || !smartEls) {
      smartKey = key; box.innerHTML = ''; smartEls = {}; camUrlSet = '';
      if (cfg.cameraEntity && cfg.camPreview !== 'off') {
        const img = h('img', { alt: '' });
        img.onerror = () => { camUrlSet = ''; };
        const cam = h('div', { class: 'cam', 'data-f': true, _act: openCamera }, [img,
          h('div', { class: 'camlbl' }, [cfg.camPreview === 'live' ? h('i', { class: 'live' }) : null, h('span', { text: cfg.cameraName || 'Haustür' })])]);
        box.appendChild(cam); smartEls.camImg = img;
      }
      const row = h('div', { class: 'ctlrow' });
      if (cfg.lightEntity) {
        const st = h('div', { class: 'st' }); const nm = h('div', { class: 'nm' });
        const el = h('div', { class: 'ctl', 'data-f': true, _act: (long) => (long ? HA.call(domainOf(cfg.lightEntity), 'toggle', cfg.lightEntity) : openLight()) },
          [icon('bulb'), h('div', {}, [nm, st])]);
        row.appendChild(el); smartEls.light = { el, st, nm };
      }
      if (cfg.fanEntity) {
        const st = h('div', { class: 'st' }); const nm = h('div', { class: 'nm' });
        const el = h('div', { class: 'ctl', 'data-f': true, _act: (long) => (long ? HA.call(domainOf(cfg.fanEntity), 'toggle', cfg.fanEntity) : openFan()) },
          [icon('fan'), h('div', {}, [nm, st])]);
        row.appendChild(el); smartEls.fan = { el, st, nm };
      }
      if (row.children.length) box.appendChild(row);
    }
    if (smartEls.light) {
      const s = HA.get(cfg.lightEntity);
      smartEls.light.nm.textContent = cfg.lightName || 'Licht';
      smartEls.light.st.textContent = lightInfo();
      smartEls.light.el.classList.toggle('on', !!s && s.state === 'on');
    }
    if (smartEls.fan) {
      const s = HA.get(cfg.fanEntity);
      smartEls.fan.nm.textContent = cfg.fanName || 'Ventilator';
      smartEls.fan.st.textContent = fanInfo();
      smartEls.fan.el.classList.toggle('on', !!s && s.state === 'on');
    }
    updateCam();
  }
  function updateCam() {
    if (!smartEls || !smartEls.camImg) return;
    if (paused) { if (camUrlSet) { smartEls.camImg.src = ''; camUrlSet = ''; } return; }
    const live = cfg.camPreview === 'live';
    const url = HA.cameraUrl(cfg.cameraEntity, live);
    if (!url) return;
    if (live) { if (url !== camUrlSet) { smartEls.camImg.src = url; camUrlSet = url; } }
    else if (!stillTimer || url !== camUrlSet) {
      camUrlSet = url; smartEls.camImg.src = url + (url.includes('?') ? '&' : '?') + '_=' + Date.now();
    }
  }
  setInterval(() => {
    if (cfg.camPreview === 'still' && smartEls && smartEls.camImg && camUrlSet && !paused) {
      smartEls.camImg.src = camUrlSet + (camUrlSet.includes('?') ? '&' : '?') + '_=' + Date.now();
    }
  }, 10000);

  function openCamera() {
    const url = HA.cameraUrl(cfg.cameraEntity, true);
    openModal(h('div', { class: 'sheet camfull' }, [
      h('h2', {}, [icon('camera'), cfg.cameraName || 'Haustür']),
      url ? h('img', { src: url }) : h('div', { text: 'Kein Kamerabild – ist Home Assistant verbunden?' }),
      h('div', { class: 'btn', 'data-f': true, style: 'margin-top:1vw;align-self:flex-start', _act: closeModal }, ['Schließen'])
    ]));
  }

  // ---- Regler
  function slider(o) {
    const fill = h('div', { class: 'fill' }), knob = h('div', { class: 'knob' }), vtxt = h('span');
    const el = h('div', { class: 'slider' + (o.cls ? ' ' + o.cls : ''), 'data-f': true }, [
      h('div', { class: 'top' }, [h('span', { text: o.label }), vtxt]), h('div', { class: 'trk' }, [fill, knob])
    ]);
    let v = o.value, t = null;
    const draw = () => { const p = (v - o.min) / (o.max - o.min) * 100; fill.style.width = p + '%'; knob.style.left = p + '%'; vtxt.textContent = o.fmt(v); };
    el._lr = (d) => {
      v = Math.min(o.max, Math.max(o.min, Math.round((v + d * o.step) / o.step) * o.step));
      draw(); el._touched = Date.now();
      clearTimeout(t); t = setTimeout(() => o.onChange(v), 250);
    };
    el._act = () => el._lr(1);
    el.setValue = (nv) => { if (Date.now() - (el._touched || 0) < 2500) return; v = nv; draw(); };
    draw();
    return el;
  }

  const COLORS = [['Rot', [0, 100], '#ff3b30'], ['Orange', [28, 100], '#ff9500'], ['Gelb', [50, 90], '#ffd60a'], ['Grün', [120, 80], '#34c759'],
    ['Türkis', [180, 70], '#5ac8d8'], ['Blau', [225, 90], '#0a64ff'], ['Lila', [275, 80], '#9b51e0'], ['Pink', [325, 70], '#ff4fa3']];

  function openLight() {
    const id = cfg.lightEntity, dom = domainOf(id);
    const s0 = HA.get(id);
    if (!s0) { toast('Licht nicht gefunden – bitte am Handy einrichten'); return; }
    const a = s0.attributes;
    const modes = a.supported_color_modes || [];
    const dimmable = dom === 'light' && modes.some((m) => m !== 'onoff');
    const hasTemp = modes.includes('color_temp');
    const hasColor = modes.some((m) => ['hs', 'rgb', 'rgbw', 'rgbww', 'xy'].includes(m));
    const tBtnTxt = h('span'); const st = h('div', { class: 'sub' });
    const tBtn = h('div', { class: 'btn', 'data-f': true, _act: () => HA.call(dom, 'toggle', id) }, [icon('power'), tBtnTxt]);
    const kids = [h('h2', {}, [icon('bulb'), cfg.lightName || nameOf(id, 'Licht')]), st, h('div', { style: 'display:flex;margin-bottom:.8vw' }, [tBtn])];
    let bri = null, temp = null;
    if (dimmable) {
      bri = slider({ label: 'Helligkeit', min: 1, max: 100, step: 5, value: a.brightness ? Math.round(a.brightness / 2.55) : 50, fmt: (v) => v + ' %',
        onChange: (v) => HA.call('light', 'turn_on', id, { brightness_pct: v }) });
      kids.push(bri);
    }
    if (hasTemp) {
      const mn = a.min_color_temp_kelvin || 2200, mx = a.max_color_temp_kelvin || 6500;
      temp = slider({ label: 'Lichtfarbe (warm – kalt)', cls: 'temp', min: mn, max: mx, step: 100, value: a.color_temp_kelvin || Math.round((mn + mx) / 2 / 100) * 100,
        fmt: (v) => v + ' K', onChange: (v) => HA.call('light', 'turn_on', id, { color_temp_kelvin: v }) });
      kids.push(temp);
    }
    if (hasColor) {
      kids.push(h('div', { class: 'section', text: 'Farbe' }));
      kids.push(h('div', { class: 'swatches' }, COLORS.map(([n, hs, css]) =>
        h('div', { class: 'sw', 'data-f': true, title: n, _act: () => { HA.call('light', 'turn_on', id, { hs_color: hs }); toast('Farbe: ' + n); } }, [h('i', { style: 'background:' + css })]))));
    }
    const upd = () => {
      const s = HA.get(id); if (!s) return;
      const on = s.state === 'on';
      tBtnTxt.textContent = on ? 'Ausschalten' : 'Einschalten';
      tBtn.classList.toggle('on', on);
      st.textContent = lightInfo();
      if (bri && s.attributes.brightness) bri.setValue(Math.round(s.attributes.brightness / 2.55));
      if (temp && s.attributes.color_temp_kelvin) temp.setValue(Math.round(s.attributes.color_temp_kelvin / 100) * 100);
    };
    upd();
    openModal(h('div', { class: 'sheet', style: 'width:40vw' }, kids), { refresh: upd });
  }

  function openFan() {
    const id = cfg.fanEntity, dom = domainOf(id);
    const s0 = HA.get(id);
    if (!s0) { toast('Ventilator nicht gefunden – bitte am Handy einrichten'); return; }
    const a = s0.attributes;
    const tBtnTxt = h('span'); const st = h('div', { class: 'sub' });
    const tBtn = h('div', { class: 'btn', 'data-f': true, _act: () => HA.call(dom, 'toggle', id) }, [icon('power'), tBtnTxt]);
    const kids = [h('h2', {}, [icon('fan'), cfg.fanName || nameOf(id, 'Ventilator')]), st, h('div', { style: 'display:flex;margin-bottom:.8vw' }, [tBtn])];
    let spd = null;
    if (dom === 'fan' && (a.percentage !== undefined || a.percentage_step)) {
      const step = a.percentage_step || 1;
      const stepped = step >= 10;
      const levels = stepped ? Math.round(100 / step) : 100;
      spd = slider({ label: 'Geschwindigkeit', min: 0, max: levels, step: stepped ? 1 : 5,
        value: stepped ? Math.round((a.percentage || 0) / step) : (a.percentage || 0),
        fmt: (v) => (v === 0 ? 'Aus' : stepped ? 'Stufe ' + v + ' von ' + levels : v + ' %'),
        onChange: (v) => HA.call('fan', 'set_percentage', id, { percentage: stepped ? Math.min(100, Math.round(v * step)) : v }) });
      kids.push(spd);
    }
    if (dom === 'fan' && Array.isArray(a.preset_modes) && a.preset_modes.length) {
      kids.push(h('div', { class: 'section', text: 'Modus' }));
      kids.push(h('div', { style: 'display:flex;gap:.6vw;flex-wrap:wrap;padding:0 .9vw' }, a.preset_modes.map((p) =>
        h('div', { class: 'btn', 'data-f': true, _act: () => HA.call('fan', 'set_preset_mode', id, { preset_mode: p }) }, [p]))));
    }
    const upd = () => {
      const s = HA.get(id); if (!s) return;
      const on = s.state === 'on';
      tBtnTxt.textContent = on ? 'Ausschalten' : 'Einschalten';
      tBtn.classList.toggle('on', on);
      st.textContent = fanInfo();
      if (spd) { const step = s.attributes.percentage_step || 1; spd.setValue(step >= 10 ? Math.round((s.attributes.percentage || 0) / step) : (s.attributes.percentage || 0)); }
    };
    upd();
    openModal(h('div', { class: 'sheet', style: 'width:40vw' }, kids), { refresh: upd });
  }

  // ------------------------------------------------------------------ Home Assistant verbinden
  function connectHa() {
    HA.configure(cfg.haUrl, cfg.haToken, [cfg.cameraEntity, cfg.lightEntity, cfg.fanEntity, cfg.dishEntity, cfg.dishStatusEntity, cfg.ovenEntity, cfg.ovenStatusEntity]);
  }
  HA.onChange(() => { renderSmart(); renderStatus(); refreshModals(); });

  // ------------------------------------------------------------------ Aufrufe von Android
  window.tvConfigChanged = () => {
    const oldFav = JSON.stringify(cfg.favorites), oldBg = cfg.bgChanged;
    loadCfg(); applyCfg(); connectHa();
    if (JSON.stringify(cfg.favorites) !== oldFav) renderApps();
    if (cfg.bgChanged !== oldBg) { loadBackgroundLists(); startBg(); }
    renderSmart(true); renderStatus(); refreshModals();
  };
  window.tvPause = () => { paused = true; updateCam(); };
  window.tvResume = () => {
    paused = false; lastKey = Date.now(); exitArt();
    const before = apps.length; loadApps(); if (apps.length !== before) renderApps();
    camUrlSet = ''; updateCam(); ensureFocus();
  };

  // ------------------------------------------------------------------ Start
  function init() {
    document.querySelectorAll('#topicons .iconbtn').forEach((b) => (b.innerHTML = ICON[b.dataset.act === 'allapps' ? 'grid' : b.dataset.act === 'art' ? 'art' : 'gear']));
    document.querySelectorAll('[data-act]').forEach((b) => {
      const a = b.dataset.act;
      b._act = () => (a === 'art' ? enterArt() : a === 'allapps' ? openAllApps() : openSettings());
    });
    loadCfg(); applyCfg();
    loadApps(); renderApps();
    loadBackgroundLists(); startBg(true);
    connectHa(); renderSmart(true); renderStatus();
    setInterval(tickClock, 5000);
    setInterval(renderStatus, 30000);
    setFocus(defaultFocus());
  }
  window.TV = { openSettings, openAllApps, openLight, openFan, openBgPicker, openSetup, enterArt, exitArt, cfg: () => cfg, setFocus, move, activate };
  init();
})();
