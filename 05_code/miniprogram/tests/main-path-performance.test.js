const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function page(file, services) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    Page: (value) => {
      definition = value;
    },
    require: (name) =>
      name.includes('utils/date') ? { formatBusinessDate: () => '2026-10-08' } : services,
    wx: {},
    Date,
    Promise,
  });
  return {
    ...definition,
    data: { ...definition.data },
    setData(value) {
      Object.assign(this.data, value);
    },
  };
}
const tick = () => new Promise((resolve) => setImmediate(resolve));
test('returning from feedback reloads daily word statuses without duplicating first load', async () => {
  let calls = 0;
  const p = page('pages/today/words/index.js', {
    dailyTaskService: {
      selectWords: async () => {
        calls++;
        return {
          tasks: [{ taskType: 'word', contentId: 'a', status: calls === 1 ? 'TODO' : 'DONE' }],
        };
      },
    },
    contentService: { get: async () => ({ meaning: 'word' }) },
  });
  p.onLoad({});
  p.onShow();
  await tick();
  assert.equal(calls, 1);
  assert.equal(p.data.words[0].status, 'TODO');
  p.onShow();
  await tick();
  assert.equal(calls, 2);
  assert.equal(p.data.words[0].status, 'DONE');
});
test('same in-flight progress read uses one request and old tab response cannot replace current tab', async () => {
  const requests = [];
  const p = page('pages/word/books/progress/index.js', {
    vocabularyBookService: {
      progress: (status) => new Promise((resolve) => requests.push({ status, resolve })),
    },
  });
  const first = p.load(true);
  const duplicate = p.load(true);
  assert.equal(requests.length, 1);
  p.data.status = 'learned';
  const latest = p.load(true);
  assert.equal(requests.length, 2);
  requests[1].resolve({ items: [{ id: 'current' }] });
  await latest;
  requests[0].resolve({ items: [{ id: 'old' }] });
  await Promise.all([first, duplicate]);
  assert.equal(p.data.items[0].id, 'current');
});
test('growth and memory parallel duplicate loads share requests but later refresh still reads server', async () => {
  let growth = 0,
    config = 0,
    sources = 0,
    resolveGrowth,
    resolveConfig;
  const p = page('pages/today/index.js', {
    growthService: {
      overview: () => {
        growth++;
        return new Promise((resolve) => {
          resolveGrowth = resolve;
        });
      },
    },
    wordMemoryService: {
      config: () => {
        config++;
        return new Promise((resolve) => {
          resolveConfig = resolve;
        });
      },
      sources: async () => {
        sources++;
        return {};
      },
    },
  });
  const a = p.loadGrowth(),
    b = p.loadGrowth(),
    c = p.loadMemoryEntry(),
    d = p.loadMemoryEntry();
  assert.equal(growth, 1);
  assert.equal(config, 1);
  resolveGrowth({});
  resolveConfig({ enabled: true });
  await Promise.all([a, b, c, d]);
  assert.equal(sources, 1);
  const refresh = p.loadGrowth();
  assert.equal(growth, 2);
  resolveGrowth({});
  await refresh;
});
test('announcement detail retries share an in-flight read but retry after completion reads again', async () => {
  let count = 0,
    resolveRead;
  const p = page('pages/announcements/detail/index.js', {
    request: () => {
      count++;
      return new Promise((resolve) => {
        resolveRead = resolve;
      });
    },
  });
  p.id = '1';
  const a = p.load(),
    b = p.load();
  assert.equal(count, 1);
  resolveRead({ title: 'new' });
  await Promise.all([a, b]);
  const c = p.load();
  assert.equal(count, 2);
  resolveRead({ title: 'updated' });
  await c;
  assert.equal(p.data.item.title, 'updated');
});
