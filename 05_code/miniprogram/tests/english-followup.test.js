const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function loadPage(file, services, wx = {}) {
  let p;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    Page: value => { p = value; },
    require: name => name.includes('/date') ? { formatBusinessDate: () => '2026-11-09' }
      : name.includes('word-study-entry') ? require('../utils/word-study-entry') : services,
    wx: { showToast() {}, ...wx }, Date, Promise, Math,
  });
  p.setData = value => Object.assign(p.data, value);
  return p;
}

test('weak practice reads its source session and creates a server-scoped retry with the same creation key', async () => {
  const writes = [], navigation = [];
  const p = loadPage('pages/word/memory/setup/index.js', { wordMemoryService: {
    result: async id => { assert.equal(id, 'origin'); return { state: 'completed', weakWordCount: 2, weakContentIds: ['available'] }; },
    sources: () => { throw Error('must not replace weak scope with today words'); },
    createSession: async (body, key) => { writes.push({body, key}); return { sessionId: 'retry' }; },
  } }, { redirectTo: value => navigation.push(value.url) });
  p.onLoad({ weakSessionId: 'origin' });
  await new Promise(setImmediate);
  assert.equal(p.data.availableWeakCount, 1);
  p.selectSource({currentTarget:{dataset:{value:'today'}}});
  assert.equal(p.data.source, 'weak');
  p.start();
  await new Promise(setImmediate);
  assert.equal(writes[0].body.source, 'weak');
  assert.equal(writes[0].body.sourceSessionId, 'origin');
  assert.equal(writes[0].body.contentIds, undefined);
  assert.equal(writes[0].body.addToReview, false);
  assert.match(navigation[0], /id=retry/);
  p.start();
  await new Promise(setImmediate);
  assert.equal(writes[0].key, writes[1].key);
});

test('unavailable replacement requires confirmation, merges busy taps and refreshes the returned version', async () => {
  let modal, resolveWrite, writes = 0;
  const p = loadPage('pages/today/words/index.js', { dailyTaskService: {
    replaceUnavailableWords: (date, version) => {
      assert.equal(date, '2026-11-09'); assert.equal(version, 4); writes++;
      return new Promise(resolve => { resolveWrite = resolve; });
    },
    selectWords: async () => ({ tasks: [], versionNo: 5, wordsUnavailableCount: 0 }),
  } }, { showModal: value => { modal = value; } });
  Object.assign(p.data, { date: '2026-11-09', loading: false, packageVersion: 4, unavailableCount: 1 });
  p.replaceUnavailable();
  assert.equal(writes, 0);
  modal.success({ confirm: false }); modal.complete();
  assert.equal(writes, 0);
  p.replaceUnavailable();
  modal.success({ confirm: true }); modal.complete();
  p.replaceUnavailable();
  assert.equal(writes, 1);
  resolveWrite({});
  await new Promise(setImmediate);
  assert.equal(p.data.packageVersion, 5);
  assert.equal(p.data.replacing, false);
});

test('historical word lists cannot request replacement', () => {
  let calls = 0;
  const p = loadPage('pages/today/words/index.js', {}, { showModal: () => calls++ });
  Object.assign(p.data, { date: '2026-11-08', loading: false, unavailableCount: 1 });
  p.replaceUnavailable();
  assert.equal(calls, 0);
});
