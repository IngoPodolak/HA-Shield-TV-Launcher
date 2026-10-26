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

  // ---------------- Lautstärke-Regler
  const vol = q('#vol'), volv = q('#volv');
  let volFixed = false, volLast = 50, volT = null;
  const paint = (r) => r.style.setProperty('--p', ((r.value - r.min) / (r.max - r.min) * 100) + '%');
  function volShow(v) { vol.value = v; volv.textContent = volFixed ? '' : v + ' %'; paint(vol); }
  fetch('/api/volume?pin=' + PIN).then((r) => r.json()).then((j) => {
    volFixed = !!j.fixed; volLast = volFixed ? 50 : j.v; volShow(volLast);
    q('#volHint').textContent = volFixed ? 'Die Lautstärke regelt dein Verstärker – der Regler schickt Lauter/Leiser-Befehle.' : '';
  }).catch(() => {});
  vol.addEventListener('input', () => {
    paint(vol);
    if (!volFixed) {
      volv.textContent = vol.value + ' %';
      clearTimeout(volT);
      volT = setTimeout(() => fetch('/api/volume?v=' + vol.value + '&pin=' + PIN, { method: 'POST' }), 80);
      return;
    }
    // Verstärker über HDMI: jede 4 % Bewegung = ein Tastendruck Lauter/Leiser
    const steps = Math.trunc((vol.value - volLast) / 4);
    if (steps !== 0) {
      for (let i = 0; i < Math.min(Math.abs(steps), 6); i++) sendKey(steps > 0 ? 'volup' : 'voldown');
      volLast += steps * 4;
    }
  });
  vol.addEventListener('change', () => { if (volFixed) { volLast = 50; setTimeout(() => volShow(50), 400); } });

  // ---------------- Nachricht an den Fernseher
  let nImage = null;
  q('#nPick').onclick = () => q('#nImg').click();
  q('#nImg').onchange = () => {
    const f = q('#nImg').files[0]; if (!f) return;
    const img = new Image();
    img.onload = () => {
      const max = 1600, sc = Math.min(1, max / Math.max(img.width, img.height));
      const c = document.createElement('canvas'); c.width = Math.round(img.width * sc); c.height = Math.round(img.height * sc);
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      c.toBlob((b) => { nImage = b; q('#nPrevImg').src = URL.createObjectURL(b); q('#nPrev').style.display = 'block'; }, 'image/jpeg', 0.88);
    };
    img.src = URL.createObjectURL(f);
    q('#nImg').value = '';
  };
  q('#nImgDel').onclick = () => { nImage = null; q('#nPrev').style.display = 'none'; };
  q('#nSend').onclick = () => {
    const t = q('#nTitle').value.trim(), m = q('#nMsg').value.trim();
    const out = q('#nMsgOut');
    if (!t && !m && !nImage) { out.textContent = 'Bitte Text oder Bild angeben.'; out.className = 'msg err'; return; }
    out.textContent = 'Sende …'; out.className = 'msg ok';
    fetch('/api/notify?pin=' + PIN + '&title=' + encodeURIComponent(t) + '&msg=' + encodeURIComponent(m), { method: 'POST', body: nImage || '' })
      .then((r) => r.json()).then((j) => {
        if (j.overlay === false) { out.textContent = 'Die Popup-Berechtigung fehlt noch am Fernseher.'; out.className = 'msg err'; return; }
        out.textContent = 'Gesendet.'; out.className = 'msg ok';
        q('#nMsg').value = ''; q('#nTitle').value = ''; nImage = null; q('#nPrev').style.display = 'none';
      }).catch(() => { out.textContent = 'Der Fernseher antwortet nicht.'; out.className = 'msg err'; });
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
  const DEF = { dashZoom: 75, layout: 'dock', material: 'clear', focus: 'glass', iconStyle: 'white', labels: 'on', textSize: 'normal', clock: '24', dim: 10, bgMode: 'fixed', bgId: '01' };
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
    const z = q('#zoom');
    if (z && document.activeElement !== z) { z.value = val('dashZoom'); q('#zoomv').textContent = z.value + ' %'; z.style.setProperty('--p', ((z.value - z.min) / (z.max - z.min) * 100) + '%'); }
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
  const zoomEl = q('#zoom');
  let zoomT = null;
  zoomEl.addEventListener('input', () => {
    q('#zoomv').textContent = zoomEl.value + ' %';
    zoomEl.style.setProperty('--p', ((zoomEl.value - zoomEl.min) / (zoomEl.max - zoomEl.min) * 100) + '%');
    clearTimeout(zoomT);
    zoomT = setTimeout(() => { state.config.dashZoom = Number(zoomEl.value); post('/api/config', { dashZoom: Number(zoomEl.value) }); }, 150);
  });

  // Nach dem Hochladen eigener Fotos die Auswahl neu laden
  const filesInput = q('#files');
  if (filesInput) filesInput.addEventListener('change', () => setTimeout(loadDesign, 2500));

  let start = 'remote';
  try { start = localStorage.getItem('tvhome-tab') || 'remote'; } catch (e) {}
  if (new URLSearchParams(location.search).get('tab')) start = new URLSearchParams(location.search).get('tab');
  showTab(['remote', 'design', 'setup'].includes(start) ? start : 'remote');
  loadDesign();
})();
