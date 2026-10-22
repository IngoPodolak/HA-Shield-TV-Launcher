/* Nur zum Testen im normalen Browser: ersetzt Android und Home Assistant durch Beispieldaten. */
(function () {
  if (window.Android) return;
  let cfg = {};
  try { cfg = JSON.parse(localStorage.getItem('mockcfg') || '{}'); } catch (e) {}
  cfg = Object.assign({
    haUrl: 'http://192.168.1.10:8123', haToken: 'x',
    cameraEntity: 'camera.haustuer', doorbellEntity: 'binary_sensor.klingel',
    lightEntity: 'light.wohnzimmer', fanEntity: 'fan.deckenventilator',
    dishEntity: 'sensor.spuelmaschine_restzeit', ovenEntity: 'sensor.backofen_fertig'
  }, cfg);
  const apps = ['Stremio', 'Netflix', 'Disney+', 'YouTube', 'Apple TV', 'Prime Video', 'Plex', 'ARD Mediathek', 'ZDF', 'Spotify', 'Kodi', 'Twitch', 'Waipu.tv', 'Joyn']
    .map((l, i) => ({ pkg: 'app.' + i, label: l, banner: true }));
  const bgs = (window.MOCK_BGS || []).map((u, i) => ({ id: String(i + 1).padStart(2, '0'), name: 'Landschaft ' + (i + 1), url: u, thumb: u }));
  window.Android = {
    getConfig: () => JSON.stringify(cfg),
    saveConfig: (p) => { Object.assign(cfg, JSON.parse(p)); localStorage.setItem('mockcfg', JSON.stringify(cfg)); },
    getApps: () => JSON.stringify(apps),
    getIcon: () => '', getBanner: () => '',
    launch: (p) => { console.log('launch', p); return true; },
    version: () => '1.0.test',
    builtinBackgrounds: () => JSON.stringify(bgs),
    customBackgrounds: () => '[]',
    bingBackgrounds: () => '[]',
    setupUrl: () => 'http://192.168.1.50:8765/?pin=4821',
    qr: () => '',
    canOverlay: () => false,
    testPopup: () => {},
    checkUpdate: () => JSON.stringify({ current: 1, latest: 1, available: false }),
    installUpdate: () => '',
    openAndroidSettings: () => {}, openOverlaySettings: () => {},
    httpGet: () => ''
  };

  const now = Date.now();
  const states = {
    'camera.haustuer': { state: 'idle', attributes: { friendly_name: 'Haustür', access_token: 'abc' } },
    'binary_sensor.klingel': { state: 'off', attributes: { friendly_name: 'Klingel' } },
    'light.wohnzimmer': { state: 'on', attributes: { friendly_name: 'Wohnzimmer', brightness: 173, color_mode: 'color_temp', color_temp_kelvin: 2900, min_color_temp_kelvin: 2200, max_color_temp_kelvin: 6500, supported_color_modes: ['color_temp', 'hs'] } },
    'fan.deckenventilator': { state: 'off', attributes: { friendly_name: 'Deckenventilator', percentage: 0, percentage_step: 33.333 } },
    'sensor.spuelmaschine_restzeit': { state: '42', attributes: { friendly_name: 'Spülmaschine Restzeit', unit_of_measurement: 'min' } },
    'sensor.backofen_fertig': { state: new Date(now + 18 * 60000).toISOString(), attributes: { friendly_name: 'Backofen Fertig', device_class: 'timestamp' } }
  };
  window.MOCK_HA = {
    attach(HA) { for (const id in states) HA.states[id] = Object.assign({ entity_id: id }, JSON.parse(JSON.stringify(states[id]))); this.HA = HA; },
    call(domain, service, id, data) {
      const s = this.HA.states[id]; if (!s) return;
      if (service === 'turn_off') s.state = 'off';
      if (service === 'turn_on') s.state = 'on';
      if (service === 'toggle') s.state = s.state === 'on' ? 'off' : 'on';
      if (data.brightness_pct !== undefined) s.attributes.brightness = Math.round(data.brightness_pct * 2.55);
      if (data.color_temp_kelvin) { s.attributes.color_temp_kelvin = data.color_temp_kelvin; s.attributes.color_mode = 'color_temp'; }
      if (data.hs_color) { s.attributes.hs_color = data.hs_color; s.attributes.color_mode = 'hs'; }
      if (data.percentage !== undefined) { s.attributes.percentage = data.percentage; s.state = data.percentage > 0 ? 'on' : 'off'; }
      this.HA.listeners.forEach((f) => f());
      return true;
    },
    cameraUrl: () => window.MOCK_CAM || ''
  };
})();
