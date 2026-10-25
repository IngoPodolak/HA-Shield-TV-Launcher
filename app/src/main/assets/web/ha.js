/* Verbindung zu Home Assistant über die WebSocket-Schnittstelle. */
(function () {
  const HA = {
    states: {},
    connected: false,
    status: 'nicht eingerichtet',
    listeners: [],
    ws: null,
    url: '',
    token: '',
    ids: [],
    msgId: 10,
    retry: 3000,
    gen: 0,
    pending: {}
  };

  HA.onChange = (fn) => HA.listeners.push(fn);
  const emit = () => HA.listeners.forEach((f) => { try { f(); } catch (e) { console.error(e); } });

  /** urls: Hauptadresse zuerst, dann Ausweichadressen. Klappt eine nicht, wird die nächste probiert. */
  HA.configure = function (urls, token, ids) {
    urls = (urls || []).map((u) => String(u || '').trim().replace(/\/+$/, '')).filter(Boolean);
    const key = JSON.stringify([urls, token, ids]);
    const same = key === HA.key;
    HA.key = key; HA.urls = urls; HA.urlIdx = 0; HA.fails = 0; HA.token = token; HA.ids = ids.filter(Boolean);
    HA.url = urls[0] || '';
    if (window.MOCK_HA) { HA.connected = true; HA.status = 'Demo'; window.MOCK_HA.attach(HA); emit(); return; }
    if (same && HA.ws) return;
    HA.connect();
  };

  HA.connect = function () {
    const gen = ++HA.gen;
    if (HA.ws) { try { HA.ws.close(); } catch (e) {} HA.ws = null; }
    HA.connected = false;
    HA.url = HA.urls.length ? HA.urls[HA.urlIdx % HA.urls.length] : '';
    if (!HA.url || !HA.token) { HA.status = HA.token ? 'keine Adresse' : 'nicht angemeldet'; emit(); return; }
    HA.status = 'verbinde…'; emit();
    let ws, authed = false;
    try { ws = new WebSocket(HA.url.replace(/^http/, 'ws') + '/api/websocket'); } catch (e) { HA.status = 'Adresse ungültig'; emit(); return; }
    HA.ws = ws;
    ws.onmessage = (ev) => {
      if (gen !== HA.gen) return;
      let m; try { m = JSON.parse(ev.data); } catch (e) { return; }
      if (m.type === 'auth_required') ws.send(JSON.stringify({ type: 'auth', access_token: HA.token }));
      else if (m.type === 'auth_invalid') { HA.status = 'Anmeldung ungültig – bitte neu anmelden'; HA.invalid = true; emit(); }
      else if (m.type === 'auth_ok') {
        authed = true; HA.fails = 0; HA.invalid = false;
        HA.connected = true; HA.status = 'verbunden (' + HA.url.replace(/^https?:\/\//, '') + ')'; HA.retry = 3000;
        if (HA.ids.length) ws.send(JSON.stringify({ id: 1, type: 'subscribe_entities', entity_ids: HA.ids }));
        emit();
      } else if (m.type === 'event' && m.id === 1) applyCompressed(m.event);
      else if (m.type === 'result' && HA.pending[m.id]) { HA.pending[m.id](m); delete HA.pending[m.id]; }
    };
    ws.onclose = () => {
      if (gen !== HA.gen) return;
      HA.connected = false;
      if (HA.invalid) { emit(); return; }
      let delay = HA.retry;
      if (!authed) {
        HA.urlIdx++; HA.fails++;
        if (HA.fails < HA.urls.length) delay = 500;
        else { HA.fails = 0; HA.retry = Math.min(HA.retry * 2, 60000); }
        HA.status = 'nicht erreichbar – versuche ' + HA.urls[HA.urlIdx % HA.urls.length].replace(/^https?:\/\//, '') + ' …';
      } else HA.status = 'getrennt – verbinde neu …';
      emit();
      setTimeout(() => { if (gen === HA.gen) HA.connect(); }, delay);
    };
  };

  /** Alle Entitäten (für die Geräteauswahl im Menü). */
  /** Beliebigen WebSocket-Befehl senden, Antwort als Promise. */
  HA.cmd = function (msg) {
    if (window.MOCK_HA) return Promise.resolve(window.MOCK_HA.cmd ? window.MOCK_HA.cmd(msg) : []);
    return new Promise((resolve, reject) => {
      if (!HA.ws || !HA.connected) { reject(new Error('Home Assistant ist nicht verbunden')); return; }
      const id = ++HA.msgId;
      HA.pending[id] = (m) => (m.success ? resolve(m.result) : reject(new Error((m.error && m.error.message) || 'Fehler')));
      HA.ws.send(JSON.stringify(Object.assign({ id }, msg)));
      setTimeout(() => { if (HA.pending[id]) { delete HA.pending[id]; reject(new Error('Keine Antwort')); } }, 15000);
    });
  };

  HA.listStates = function () {
    if (window.MOCK_HA) return Promise.resolve(window.MOCK_HA.list());
    return new Promise((resolve, reject) => {
      if (!HA.ws || !HA.connected) { reject(new Error('Home Assistant ist nicht verbunden')); return; }
      const id = ++HA.msgId;
      HA.pending[id] = (m) => (m.success ? resolve(m.result) : reject(new Error('Fehler beim Laden')));
      HA.ws.send(JSON.stringify({ id, type: 'get_states' }));
      setTimeout(() => { if (HA.pending[id]) { delete HA.pending[id]; reject(new Error('Keine Antwort')); } }, 15000);
    });
  };

  function applyCompressed(ev) {
    if (ev.a) for (const id in ev.a) {
      const s = ev.a[id];
      HA.states[id] = { entity_id: id, state: s.s, attributes: s.a || {}, last_changed: s.lc };
    }
    if (ev.c) for (const id in ev.c) {
      const st = HA.states[id] || (HA.states[id] = { entity_id: id, state: '', attributes: {} });
      const ch = ev.c[id];
      if (ch['+']) {
        if (ch['+'].s !== undefined) st.state = ch['+'].s;
        if (ch['+'].a) Object.assign(st.attributes, ch['+'].a);
        if (ch['+'].lc) st.last_changed = ch['+'].lc;
      }
      if (ch['-'] && ch['-'].a) ch['-'].a.forEach((k) => delete st.attributes[k]);
    }
    if (ev.r) ev.r.forEach((id) => delete HA.states[id]);
    emit();
  }

  HA.call = function (domain, service, entityId, data) {
    if (window.MOCK_HA) return window.MOCK_HA.call(domain, service, entityId, data || {});
    if (!HA.ws || !HA.connected) return false;
    const id = ++HA.msgId;
    HA.ws.send(JSON.stringify({ id, type: 'call_service', domain, service, service_data: data || {}, target: { entity_id: entityId } }));
    return true;
  };

  HA.get = (id) => (id ? HA.states[id] : null);

  /** Adresse des Kamerabilds (live = MJPEG-Stream, sonst Einzelbild). */
  HA.cameraUrl = function (id, live) {
    const s = HA.get(id);
    if (window.MOCK_HA) return window.MOCK_HA.cameraUrl(id);
    if (!s || !s.attributes.access_token) return '';
    return HA.url + '/api/' + (live ? 'camera_proxy_stream/' : 'camera_proxy/') + id + '?token=' + s.attributes.access_token;
  };

  window.HA = HA;
})();
