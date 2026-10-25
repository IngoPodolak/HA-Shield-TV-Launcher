/* Neue Handy-Seite: Reiter, Fernbedienung und Design-Auswahl. Nutzt $, api, post, PIN aus dem Einrichtungs-Skript. */
(function () {
  'use strict';
  const q = (sel, r) => (r || document).querySelector(sel);
  const qa = (sel, r) => Array.from((r || document).querySelectorAll(sel));
  let state = { config: {} };

  // ---------------- Reiter
  function showTab(name) {
    qa('.tab').forEach((t) => t.classList.toggle('on', t.id === 'tab-' + name));
    qa('nav button').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
    try { localStorage.setItem('tvhome-tab', name); } catch (e) {}
    window.scrollTo(0, 0);
    if (name === 'remote') remoteCheck();
  }
  qa('nav button').forEach((b) => (b.onclick = () => showTab(b.dataset.tab)));

  // ---------------- Fernbedienung
  const banner = q('#remoteBanner');
  function showBanner(t) { banner.textContent = t; banner.style.display = t ? 'block' : 'none'; }
  function sendKey(k) {
    if (navigator.vibrate) try { navigator.vibrate(12); } catch (e) {}
    fetch('/api/key?k=' + encodeURIComponent(k) + '&pin=' + PIN, { method: 'POST' })
      .then((r) => r.json()).then((j) => { if (j.error) showBanner(j.error); else showBanner(''); })
      .catch(() => showBanner('Der Fernseher antwortet nicht. Ist er an und im gleichen WLAN?'));
  }
  let checked = false;
  function remoteCheck() {
    if (checked) return; checked = true;
    showBanner('Verbinde mit dem Fernseher … Falls dort „Debugging zulassen?“ erscheint: „Immer von diesem Computer zulassen“ anhaken und OK drücken.');
    fetch('/api/remote-test?pin=' + PIN, { method: 'POST' }).then((r) => r.json())
      .then((j) => showBanner(j.ok ? '' : (j.error || 'Keine Verbindung zum Fernseher.')))
      .catch(() => showBanner('Der Fernseher antwortet nicht.'));
  }
  qa('[data-k]').forEach((b) => {
    let t1 = null, t2 = null;
    const repeat = b.dataset.repeat || b.closest('.dpad') && b.dataset.k !== 'ok';
    const stop = () => { clearTimeout(t1); clearInterval(t2); t1 = t2 = null; b.classList.remove('down-now'); };
    b.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      b.classList.add('down-now');
      sendKey(b.dataset.k);
      if (repeat) t1 = setTimeout(() => { t2 = setInterval(() => sendKey(b.dataset.k), 170); }, 420);
    });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => b.addEventListener(ev, stop));
    b.addEventListener('contextmenu', (e) => e.preventDefault());
  });
  q('#txtSend').onclick = () => {
    const t = q('#txt').value; if (!t) return;
    fetch('/api/text?pin=' + PIN, { method: 'POST', body: t }).then(() => { q('#txt').value = ''; });
  };

  // ---------------- Design
  const STYLE = [
    { key: 'material', label: 'Material', opts: [['clear', 'Klar'], ['frost', 'Milchglas'], ['dark', 'Dunkel'], ['none', 'Ohne']] },
    { key: 'focus', label: 'Auswahlrahmen', opts: [['glass', 'Glas'], ['glow', 'Leuchten'], ['lift', 'Vergrößern'], ['outline', 'Rahmen']] },
    { key: 'iconStyle', label: 'App-Logos', opts: [['white', 'Weißes Logo'], ['bannerwhite', 'Weißer Schriftzug'], ['color', 'Farbig'], ['banner', 'TV-Banner']] },
    { key: 'labels', label: 'App-Namen', opts: [['on', 'Anzeigen'], ['off', 'Ausblenden']] },
    { key: 'textSize', label: 'Schrift', opts: [['normal', 'Normal'], ['big', 'Groß']] },
    { key: 'clock', label: 'Uhr', opts: [['24', '24 Std.'], ['12', '12 Std.'], ['off', 'Aus']] },
    { key: 'dim', label: 'Hintergrund abdunkeln', opts: [[0, 'Aus'], [10, '10 %'], [20, '20 %'], [30, '30 %'], [45, '45 %']] }
  ];
  const DEF = { layout: 'dock', material: 'clear', focus: 'glass', iconStyle: 'white', labels: 'on', textSize: 'normal', clock: '24', dim: 10, bgMode: 'fixed', bgId: '01' };
  const val = (k) => (state.config[k] !== undefined ? state.config[k] : DEF[k]);
  function set(patch) {
    Object.assign(state.config, patch);
    renderDesign();
    return post('/api/config', patch).catch(() => {});
  }

  let builtin = [], custom = [];
  function bgUrl(id) {
    if (!id) return '';
    if (String(id).startsWith('custom-')) return '/bg/' + encodeURIComponent(String(id).slice(7)) + '?pin=' + PIN;
    return '/api/bgthumb?id=' + encodeURIComponent(id) + '&pin=' + PIN;
  }
  function currentBgUrl() {
    const m = val('bgMode');
    if (m === 'fixed') return bgUrl(val('bgId'));
    if (m === 'custom' && custom.length) return bgUrl('custom-' + custom[0].file);
    return builtin.length ? bgUrl(builtin[0].id) : '';
  }

  function renderDesign() {
    const cur = currentBgUrl();
    const bgEl = q('#bgimg'); if (cur) bgEl.style.backgroundImage = 'url("' + cur + '")';

    // Layouts
    const L = q('#layouts'); L.innerHTML = '';
    [['dock', 'Dock', 'Apps als Leiste unten, wie im Kino'], ['list', 'Liste', 'Große App-Namen links, ruhiger Look']].forEach(([k, n, d]) => {
      const b = document.createElement('button');
      b.className = 'layout' + (val('layout') === k ? ' on' : '');
      b.innerHTML = '<div class="pv"></div><div class="nm"></div><div class="ds"></div>';
      const pv = b.querySelector('.pv');
      if (cur) pv.style.backgroundImage = 'url("' + cur + '")';
      pv.innerHTML = '<div class="clk"></div>' + (k === 'dock' ? '<div class="dock"><i></i><i></i><i></i><i></i><i></i></div>' : '<div class="lst"><i></i><i></i><i></i><i></i><i></i></div>');
      b.querySelector('.nm').textContent = n; b.querySelector('.ds').textContent = d;
      b.onclick = () => set({ layout: k });
      L.appendChild(b);
    });

    // Stil
    const C = q('#styleCard'); C.innerHTML = '';
    STYLE.forEach((s) => {
      const lbl = document.createElement('div'); lbl.className = 'opt'; lbl.textContent = s.label; C.appendChild(lbl);
      const row = document.createElement('div'); row.className = 'chips';
      s.opts.forEach(([v, t]) => {
        const c = document.createElement('button');
        c.className = 'chip' + (String(val(s.key)) === String(v) ? ' on' : '');
        c.textContent = t;
        c.onclick = () => { const p = {}; p[s.key] = v; set(p); };
        row.appendChild(c);
      });
      C.appendChild(row);
    });
    if (C.firstChild) C.firstChild.style.marginTop = '2px';

    // Hintergrund-Modus
    const M = q('#bgModes'); M.innerHTML = '';
    [['fixed', 'Festes Bild'], ['rotate', 'Wechselnd'], ['custom', 'Eigene Fotos'], ['bing', 'Bing Bild des Tages']].forEach(([v, t]) => {
      const c = document.createElement('button');
      c.className = 'chip' + (val('bgMode') === v ? ' on' : '');
      c.textContent = t;
      c.onclick = () => set({ bgMode: v });
      M.appendChild(c);
    });

    // Hintergrund-Bilder
    const G = q('#bgGrid'); G.innerHTML = '';
    const items = builtin.map((b) => ({ id: b.id, name: b.name })).concat(custom.map((c) => ({ id: 'custom-' + c.file, name: 'Eigenes Foto' })));
    items.forEach((it) => {
      const b = document.createElement('button');
      b.className = 'bg' + (val('bgMode') === 'fixed' && String(val('bgId')) === String(it.id) ? ' on' : '');
      b.style.backgroundImage = 'url("' + bgUrl(it.id) + '")';
      b.innerHTML = '<span></span>'; b.querySelector('span').textContent = it.name;
      b.onclick = () => set({ bgMode: 'fixed', bgId: it.id });
      G.appendChild(b);
    });
  }

  function loadDesign() {
    Promise.all([
      api('/api/state').catch(() => ({ config: {} })),
      api('/api/backgrounds/builtin').catch(() => []),
      api('/api/backgrounds').catch(() => [])
    ]).then(([s, b, c]) => {
      state = s || { config: {} }; builtin = b || []; custom = c || [];
      renderDesign();
    });
  }
  // Nach dem Hochladen eigener Fotos die Auswahl neu laden
  const filesInput = q('#files');
  if (filesInput) filesInput.addEventListener('change', () => setTimeout(loadDesign, 2500));

  let start = 'remote';
  try { start = localStorage.getItem('tvhome-tab') || 'remote'; } catch (e) {}
  if (new URLSearchParams(location.search).get('tab')) start = new URLSearchParams(location.search).get('tab');
  showTab(['remote', 'design', 'setup'].includes(start) ? start : 'remote');
  loadDesign();
})();
