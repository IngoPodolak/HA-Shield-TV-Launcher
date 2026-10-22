/* Einfache Linien-Symbole (SVG), Farbe folgt dem Text. */
(function () {
  const s = (p, extra) =>
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"' +
    (extra || '') + '>' + p + '</svg>';
  window.ICON = {
    art: s('<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 16l-5-5-9 9"/>'),
    grid: s('<rect x="4" y="4" width="6" height="6" rx="1.2"/><rect x="14" y="4" width="6" height="6" rx="1.2"/><rect x="4" y="14" width="6" height="6" rx="1.2"/><rect x="14" y="14" width="6" height="6" rx="1.2"/>'),
    gear: s('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>'),
    bulb: s('<path d="M9 18h6"/><path d="M10 21h4"/><path d="M12 3a6 6 0 0 0-3.6 10.8c.6.5 1 1.2 1.1 2V17h5v-1.2c.1-.8.5-1.5 1.1-2A6 6 0 0 0 12 3z"/>'),
    fan: s('<circle cx="12" cy="12" r="1.6"/><path d="M12 10.4c-.2-3 .6-6.4 3.4-6.4 2.2 0 2.8 2.6 1 4.2-1.2 1.1-2.9 1.6-4.4 2.2z"/><path d="M13.4 12.8c2.5 1.6 4.9 4.2 3.5 6.6-1.1 1.9-3.6 1.1-4.1-1.2-.4-1.6 0-3.3.6-5.4z"/><path d="M10.6 12.8c-2.7 1.4-6.1 2.2-7.5-.2-1.1-1.9.9-3.7 3.1-3 1.6.5 2.9 1.7 4.4 3.2z"/>'),
    camera: s('<path d="M15 10l4.6-2.3A1 1 0 0 1 21 8.6v6.8a1 1 0 0 1-1.4.9L15 14"/><rect x="3" y="6" width="12" height="12" rx="2"/>'),
    bell: s('<path d="M10 5a2 2 0 1 1 4 0 7 7 0 0 1 4 6v3l2 3H4l2-3v-3a7 7 0 0 1 4-6"/><path d="M9 17v1a3 3 0 0 0 6 0v-1"/>'),
    dishwasher: s('<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M4 8h16"/><path d="M8 5.5h.01M11 5.5h.01"/><circle cx="12" cy="14.5" r="3.5"/>'),
    oven: s('<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 8h18"/><rect x="7" y="11" width="10" height="6" rx="1"/><path d="M7 5.5h.01M10 5.5h.01M17 5.5h.01"/>'),
    power: s('<path d="M7 6a7.5 7.5 0 1 0 10 0"/><path d="M12 4v8"/>'),
    check: s('<path d="M5 12l5 5L20 7"/>'),
    star: s('<path d="M12 17.8l-6.2 3.2 1.2-6.9-5-4.9 6.9-1 3.1-6.3 3.1 6.3 6.9 1-5 4.9 1.2 6.9z"/>'),
    left: s('<path d="M15 6l-6 6 6 6"/>'),
    right: s('<path d="M9 6l6 6-6 6"/>'),
    phone: s('<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 17h2"/>'),
    update: s('<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>'),
    android: s('<rect x="5" y="9" width="14" height="10" rx="2"/><path d="M8 9a4 4 0 0 1 8 0"/><path d="M9 6L8 4M15 6l1-2"/>'),
    image: s('<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 15l5-5 4 4 3-3 6 6"/>')
  };
})();
