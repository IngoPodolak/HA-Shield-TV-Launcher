/* Fernbedienungs-Steuerung für das HA-Dashboard auf dem Fernseher (wird in die HA-Seite eingefügt). */
(function () {
  if (window.tvDash) return;
  const MODE = window.TV_DASH_MODE || 'view';
  const DASH = window.TV_DASH_PATH || 'lovelace';

  /** Alle Elemente eines Typs finden – auch innerhalb der Shadow-DOMs von HA. */
  function deepAll(selector, root, out, depth) {
    root = root || document; out = out || []; depth = depth || 0;
    if (depth > 40) return out;
    root.querySelectorAll(selector).forEach((e) => out.push(e));
    root.querySelectorAll('*').forEach((e) => { if (e.shadowRoot) deepAll(selector, e.shadowRoot, out, depth + 1); });
    return out;
  }
  function deepFirst(selector) { return deepAll(selector)[0] || null; }

  // ---------- Detailfenster erkennen (dann übernimmt die Tastatur-Steuerung in Android)
  let lastDialog = false;
  function dialogOpen() {
    const d = deepAll('ha-more-info-dialog, ha-dialog[open], ha-md-dialog[open], dialog[open]');
    return d.some((e) => {
      if (e.tagName === 'HA-MORE-INFO-DIALOG') { const sr = e.shadowRoot; return !!(sr && sr.querySelector('ha-dialog[open], ha-md-dialog[open], ha-adaptive-dialog')); }
      return true;
    });
  }
  setInterval(() => {
    const open = dialogOpen();
    if (open !== lastDialog) {
      lastDialog = open;
      try { TVDash.setDialog(open); } catch (e) {}
      if (!open) setTimeout(() => focusCard(current), 200);
    }
  }, 500);

  // ---------- Ansichten (Tabs) wechseln
  function views() {
    const root = deepFirst('hui-root');
    const v = root && root.lovelace && root.lovelace.config && root.lovelace.config.views;
    return Array.isArray(v) ? v : [];
  }
  function currentViewIndex(vs) {
    const seg = location.pathname.split('/').filter(Boolean)[1];
    if (seg === undefined) return 0;
    const i = vs.findIndex((v, n) => (v.path || String(n)) === seg);
    return i < 0 ? 0 : i;
  }
  function gotoView(delta) {
    const vs = views();
    if (vs.length < 2) return false;
    const i = currentViewIndex(vs);
    const j = (i + delta + vs.length) % vs.length;
    const path = '/' + location.pathname.split('/').filter(Boolean)[0] + '/' + (vs[j].path || j);
    history.pushState(null, '', path);
    window.dispatchEvent(new CustomEvent('location-changed', { detail: { replace: false } }));
    current = null;
    setTimeout(() => { window.scrollTo(0, 0); scrollers().forEach((s) => (s.scrollTop = 0)); }, 100);
    toast((vs[j].title || 'Ansicht ' + (j + 1)) + '  (' + (j + 1) + '/' + vs.length + ')');
    return true;
  }

  // ---------- Scrollen
  function scrollers() {
    const list = [];
    const root = deepFirst('hui-root');
    if (root && root.shadowRoot) root.shadowRoot.querySelectorAll('#view, .view, .content').forEach((e) => list.push(e));
    return list.filter((e) => e.scrollHeight > e.clientHeight + 5);
  }
  function scroll(dir) {
    const amount = window.innerHeight * 0.45 * dir;
    window.scrollBy({ top: amount, behavior: 'smooth' });
    scrollers().forEach((s) => s.scrollBy({ top: amount, behavior: 'smooth' }));
  }

  // ---------- Karten anwählen (Modus „Bedienen“)
  let current = null;
  const HL = '0 0 0 3px rgba(255,255,255,.95), 0 0 0 6px rgba(0,0,0,.35)';
  function cards() {
    const all = deepAll('ha-card').filter((c) => {
      const r = c.getBoundingClientRect();
      return r.width > 20 && r.height > 20 && getComputedStyle(c).visibility !== 'hidden';
    });
    // Innerste Karten bevorzugen (Stapel-Karten enthalten weitere Karten)
    return all.filter((c) => !all.some((o) => o !== c && containsDeep(c, o)));
  }
  function containsDeep(outer, inner) {
    let n = inner;
    while (n) {
      if (n === outer) return true;
      n = n.parentNode || n.host;
    }
    return false;
  }
  function focusCard(c) {
    if (current && current !== c) { current.style.boxShadow = current._tvShadow || ''; }
    current = c;
    if (!c) return;
    if (c._tvShadow === undefined) c._tvShadow = c.style.boxShadow;
    c.style.boxShadow = HL;
    c.style.transition = 'box-shadow .12s';
    c.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }
  function moveFocus(dir) {
    const list = cards();
    if (!list.length) { scroll(dir === 'up' ? -1 : dir === 'down' ? 1 : 0); return; }
    if (!current || !list.includes(current)) {
      list.sort((a, b) => { const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect(); return (ra.top - rb.top) || (ra.left - rb.left); });
      const vis = list.find((c) => c.getBoundingClientRect().bottom > 0) || list[0];
      focusCard(vis); return;
    }
    const c = current.getBoundingClientRect();
    const cx = c.left + c.width / 2, cy = c.top + c.height / 2;
    let best = null, bestScore = Infinity;
    list.forEach((el) => {
      if (el === current) return;
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2, y = r.top + r.height / 2;
      let primary, orth, overlap;
      if (dir === 'right') { if (x <= cx + 2 || r.left < c.left + 4) return; primary = r.left - c.right; orth = Math.abs(y - cy); overlap = r.top < c.bottom && r.bottom > c.top; }
      if (dir === 'left') { if (x >= cx - 2 || r.right > c.right - 4) return; primary = c.left - r.right; orth = Math.abs(y - cy); overlap = r.top < c.bottom && r.bottom > c.top; }
      if (dir === 'down') { if (y <= cy + 2 || r.top < c.top + 4) return; primary = r.top - c.bottom; orth = Math.abs(x - cx); overlap = r.left < c.right && r.right > c.left; }
      if (dir === 'up') { if (y >= cy - 2 || r.bottom > c.bottom - 4) return; primary = c.top - r.bottom; orth = Math.abs(x - cx); overlap = r.left < c.right && r.right > c.left; }
      const score = Math.max(primary, 0) + orth * (overlap ? 0.3 : 2.5);
      if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) focusCard(best);
    else if (dir === 'left' || dir === 'right') gotoView(dir === 'left' ? -1 : 1);
    else scroll(dir === 'up' ? -1 : 1);
  }
  /** Tiefstes Element an einem Bildschirmpunkt (durch alle Shadow-DOMs hindurch). */
  function deepPoint(x, y) {
    let el = document.elementFromPoint(x, y);
    while (el && el.shadowRoot) {
      const n = el.shadowRoot.elementFromPoint(x, y);
      if (!n || n === el) break;
      el = n;
    }
    return el;
  }
  function activate() {
    if (!current) { moveFocus('down'); return; }
    const r = current.getBoundingClientRect();
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    const target = deepPoint(x, y) || current;
    const opts = { bubbles: true, composed: true, cancelable: true, clientX: x, clientY: y, view: window };
    target.dispatchEvent(new MouseEvent('mousedown', opts));
    target.dispatchEvent(new MouseEvent('mouseup', opts));
    target.dispatchEvent(new MouseEvent('click', opts));
  }

  // ---------- kurzer Hinweis unten
  let toastEl = null, toastT = null;
  function toast(t) {
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.style.cssText = 'position:fixed;left:50%;bottom:4vh;transform:translateX(-50%);z-index:99999;padding:10px 22px;border-radius:99px;' +
        'background:rgba(20,20,24,.85);color:#fff;font:500 18px Roboto,sans-serif;transition:opacity .25s;pointer-events:none;opacity:0';
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = t; toastEl.style.opacity = '1';
    clearTimeout(toastT); toastT = setTimeout(() => (toastEl.style.opacity = '0'), 1800);
  }

  window.tvDash = {
    key(k) {
      if (MODE === 'control') {
        if (k === 'ok') activate(); else moveFocus(k);
      } else {
        if (k === 'left') gotoView(-1);
        else if (k === 'right') gotoView(1);
        else if (k === 'up') scroll(-1);
        else if (k === 'down') scroll(1);
      }
    }
  };
  setTimeout(() => toast(MODE === 'control' ? 'Steuerkreuz: Karte wählen · OK: schalten · Zurück: Startseite' : '◀ ▶ Ansicht wechseln · ▲ ▼ scrollen · Zurück: Startseite'), 1500);
})();
