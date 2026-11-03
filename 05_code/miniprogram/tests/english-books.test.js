const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function reader(service = {}) {
  let spec;
  const storage = new Map();
  const calls = [];
  const wx = {
    setStorageSync: (k, v) => storage.set(k, v),
    getStorageSync: (k) => storage.get(k),
    removeStorageSync: (k) => storage.delete(k),
    showToast: () => {},
    pageScrollTo: () => {},
    nextTick: (f) => f(),
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, '../pages/english-books/reader/index.js'), 'utf8'),
    {
      Page: (p) => (spec = p),
      require: () => service,
      wx,
      getApp: () => ({ globalData: { accessToken: 'test-token' } }),
      setTimeout,
      clearTimeout,
      Date,
      Promise,
    },
  );
  spec.data = JSON.parse(JSON.stringify(spec.data));
  spec.setData = function (values) {
    for (const [key, value] of Object.entries(values)) {
      const bits = key.split('.');
      let obj = this.data;
      for (const bit of bits.slice(0, -1)) obj = obj[bit];
      obj[bits.at(-1)] = value;
    }
  };
  spec.bookId = 'book';
  spec.pendingKey = 'pending';
  spec.progressRevision = 0;
  spec.data.book = { chapters: [{ id: 'ch1' }, { id: 'ch2' }] };
  spec.data.chapter = {
    id: 'ch1',
    pictures: [],
    paragraphs: [{ id: 'p1' }],
    audio: [
      { key: 'a1', assetId: 'asset1', durationSeconds: 10 },
      { key: 'a2', assetId: 'asset2', durationSeconds: 20 },
    ],
  };
  spec.offsets = [0, 10];
  spec.data.duration = 30;
  spec.audio = {
    pause: () => calls.push('pause'),
    play: () => calls.push('play'),
    stop: () => calls.push('stop'),
    destroy: () => calls.push('destroy'),
  };
  return { page: spec, storage, calls };
}
test('chapter seek converts slider fraction to the correct recording and local offset', async () => {
  const { page } = reader();
  let result;
  page.startClip = (i, s) => (result = [i, s]);
  page.seek({ detail: { value: 500 } });
  assert.deepEqual(result, [1, 5]);
});
test('signed media response cannot restart audio after leaving the page', async () => {
  let release;
  const { page, calls } = reader({ media: () => new Promise((r) => (release = r)) });
  const waiting = page.startClip(0, 4);
  page.dead = true;
  release({ url: 'https://example.test/audio' });
  await waiting;
  assert(!calls.includes('play'));
});
test('concurrent progress writes use successive confirmed revisions and preserve completion', async () => {
  const revisions = [];
  const { page, storage } = reader({
    progress: async (id, p) => {
      revisions.push(p.revision);
      return { revision: p.revision + 1, completed: p.completeChapterId ? ['ch1'] : [] };
    },
  });
  page.reading = { chapterId: 'ch1', paragraphId: 'p1' };
  await Promise.all([page.saveProgress({ completeChapterId: 'ch1' }), page.saveProgress()]);
  assert.deepEqual(revisions, [0, 1]);
  assert.equal(page.progressRevision, 2);
  assert.equal(storage.size, 0);
});
test('a version conflict blocks stale replay and retains the local unsynced record', async () => {
  let calls = 0;
  const { page, storage } = reader({
    progress: async () => {
      calls++;
      throw Object.assign(new Error('conflict'), { statusCode: 409 });
    },
  });
  await page.saveProgress();
  await page.saveProgress();
  assert.equal(calls, 1);
  assert.equal(page.conflicted, true);
  assert(storage.has('pending'));
});
test('upload failure keeps a completion action for retry', async () => {
  let fail = true;
  const payloads = [];
  const { page } = reader({
    progress: async (id, p) => {
      payloads.push(p);
      if (fail) throw new Error('offline');
      return { revision: 1, completed: ['ch1'] };
    },
  });
  await page.saveProgress({ completeChapterId: 'ch1' });
  fail = false;
  await page.saveProgress();
  assert.equal(payloads[1].completeChapterId, 'ch1');
});
test('audio advances through recordings without automatically marking a chapter complete', async () => {
  const { page } = reader();
  let next;
  page.clip = 0;
  page.startClip = (i, s) => {
    next = [i, s];
  };
  page.saveProgress = () => {
    throw new Error('must not complete chapter');
  };
  await page.audioEnded();
  assert.deepEqual(next, [1, 0]);
});
test('time events from the previous source are ignored during a seek', () => {
  const { page } = reader();
  page.switchingAudio = true;
  page.audio.currentTime = 9;
  page.timeUpdate();
  assert.equal(page.listening, undefined);
});
test('a queued save also preserves a completion when the preceding network call fails', async () => {
  let attempts = 0;
  const payloads = [];
  const { page } = reader({
    progress: async (id, p) => {
      payloads.push(p);
      if (++attempts === 1) throw new Error('offline');
      return { revision: 1, completed: ['ch1'] };
    },
  });
  await Promise.all([page.saveProgress({ completeChapterId: 'ch1' }), page.saveProgress()]);
  assert.equal(payloads[1].completeChapterId, 'ch1');
});
