const DEFAULTS = Object.freeze({ defaultAnswerMode: 'visible', autoPlayEnabled: true, autoPlayAccent: 'uk', autoPlayCount: 3, autoPlayIntervalMs: 1500, rowVersion: 0 });
const ACCENT_OPTIONS = Object.freeze([{ value: 'uk', label: '英式' }, { value: 'us', label: '美式' }]);
const COUNT_OPTIONS = Object.freeze([1, 2, 3, 4, 5]);
const INTERVAL_OPTIONS = Object.freeze([{ value: 1000, label: '1 秒' }, { value: 1500, label: '1.5 秒' }, { value: 2000, label: '2 秒' }]);

function normalize(value) {
  const input = value || {};
  const count = Number(input.autoPlayCount);
  const interval = Number(input.autoPlayIntervalMs);
  return {
    defaultAnswerMode: input.defaultAnswerMode === 'hidden' ? 'hidden' : 'visible',
    autoPlayEnabled: input.autoPlayEnabled !== false,
    autoPlayAccent: input.autoPlayAccent === 'us' ? 'us' : 'uk',
    autoPlayCount: COUNT_OPTIONS.includes(count) ? count : DEFAULTS.autoPlayCount,
    autoPlayIntervalMs: INTERVAL_OPTIONS.some(item => item.value === interval) ? interval : DEFAULTS.autoPlayIntervalMs,
    rowVersion: Number(input.rowVersion) || 0
  };
}

function nextPlaybackDelay(playsFinished, total, intervalMs) {
  return playsFinished < total ? Math.max(1000, Math.min(2000, Number(intervalMs) || DEFAULTS.autoPlayIntervalMs)) : null;
}

module.exports = { defaults: () => Object.assign({}, DEFAULTS), normalize, nextPlaybackDelay, accentOptions: () => ACCENT_OPTIONS.map(item => Object.assign({}, item)), countOptions: () => COUNT_OPTIONS.slice(), intervalOptions: () => INTERVAL_OPTIONS.map(item => Object.assign({}, item)) };
