const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function page(file, services) {
  let p;
  const navigation = [], messages = [];
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    Page: (value) => { p = value; },
    require: (name) => name.includes('/date')
      ? { formatBusinessDate: () => '2026-11-08', formatBusinessChineseDate: () => '11月8日' }
      : name.includes('word-study-entry') ? require('../utils/word-study-entry') : services,
    wx: {
      showLoading() {}, hideLoading() {},
      navigateTo: ({ url }) => navigation.push(url),
      showToast: ({ title }) => messages.push(title),
    },
    Date, Promise,
  });
  p.data = { ...p.data };
  p.setData = (value) => Object.assign(p.data, value);
  p.data.today = '2026-11-08';
  return { p, navigation, messages };
}
const stale = { type: 'word', total: 1, done: 0, items: [{ contentId: 'old' }] };
const fixed = { tasks: [{ taskType: 'word', id: 'new-task', contentId: 'selected', status: 'TODO', meaning: '释义', versionNo: 3 }] };
const selectedQueue = { current: { contentId: 'selected', version: 3 }, remaining: 1 };

test('first click waits for persisted selection and concurrent taps share the request', async () => {
  let finish, calls = 0;
  const { p, navigation } = page('pages/today/index.js', {
    wordStudyService: { queue: (mode) => {
      assert.equal(mode, 'daily'); calls++;
      return new Promise((resolve) => { finish = resolve; });
    } },
  });
  const one = p.openWordGroup(stale), two = p.openWordGroup(stale);
  assert.equal(one, two);
  assert.equal(calls, 1);
  assert.equal(navigation.length, 0);
  finish(selectedQueue);
  await one;
  assert.match(navigation[0], /id=selected&daily=1$/);
});

test('failed selection does not navigate to provisional words and can be retried', async () => {
  let fail = true;
  const { p, navigation, messages } = page('pages/today/index.js', {
    wordStudyService: { queue: async () => { if (fail) throw Error('网络失败'); return selectedQueue; } },
  });
  await p.openWordGroup(stale);
  assert.equal(navigation.length, 0);
  assert.equal(messages[0], '网络失败');
  fail = false;
  await p.openWordGroup(stale);
  assert.equal(navigation.length, 1);
});

test('a completed fixed range opens its list instead of requesting another word', async () => {
  const { p, navigation } = page('pages/today/index.js', {
    wordStudyService: { queue: async () => ({ current: null, remaining: 0, allCompleted: true }) },
  });
  await p.openWordGroup(stale);
  assert.equal(navigation[0], '/pages/today/words/index?date=2026-11-08');
});

test('a skipped word in today list resumes the daily queue while recognized words remain read only', async () => {
  const { p, navigation } = page('pages/today/words/index.js', {
    wordStudyService: { queue: async (mode, preferred) => {
      assert.equal(mode, 'daily'); assert.equal(preferred, 'selected'); return selectedQueue;
    } },
  });
  p.data.date = '2026-11-08';
  p.data.words = [{ ...fixed.tasks[0], status: 'SKIPPED' }];
  await p.openWord({ currentTarget: { dataset: { id: 'selected' } } });
  assert.match(navigation[0], /id=selected&daily=1/);
  p.data.words[0].status = 'DONE';
  p.openWord({ currentTarget: { dataset: { id: 'selected' } } });
  assert.match(navigation[1], /readonly=1/);
});

test('today list reuses selection while past date lists only read history', async () => {
  const calls = [];
  const { p } = page('pages/today/words/index.js', {
    dailyTaskService: {
      selectWords: async (date) => { calls.push(['select', date]); return fixed; },
      day: async (date) => { calls.push(['read', date]); return fixed; },
    },
    contentService: { get: async () => { throw Error('must not fetch each word detail'); } },
  });
  p.data.date = '2026-11-08';
  await p.load();
  await p.load();
  p.data.date = '2026-11-07';
  await p.load();
  assert.deepEqual(calls, [['select','2026-11-08'],['select','2026-11-08'],['read','2026-11-07']]);
  assert.equal(p.data.words[0].contentId, 'selected');
  assert.equal(p.data.words[0].meaning, '释义');
});

test('server-ready word overrides a waiting first task on homepage reentry', async () => {
  const { p, navigation } = page('pages/today/index.js', {
    wordStudyService: { queue: async () => ({ current: { contentId: 'ready-next' }, remaining: 2 }) },
  });
  await p.openWordGroup({ ...stale, items: [{ contentId: 'waiting', status: 'DOING' }] });
  assert.match(navigation[0], /id=ready-next&daily=1/);
});

test('empty but unavailable daily queue is explained without claiming completion', async () => {
  const { p, navigation, messages } = page('pages/today/index.js', {
    wordStudyService: { queue: async () => ({ current: null, remaining: 0, unfinishedCount: 2, unavailableCount: 2, allCompleted: false }) },
  });
  await p.openWordGroup(stale);
  assert.match(messages[0], /暂不可用/);
  assert.doesNotMatch(messages[0], /已学完/);
  assert.match(navigation[0], /today\/words/);
});

test('word list reads all meanings and selection explanation in a single request', async () => {
  let reads = 0, details = 0;
  const { p } = page('pages/today/words/index.js', {
    dailyTaskService: { selectWords: async () => { reads++; return {
      ...fixed, wordsBookName: '大学英语四级', wordsSelectedAt: '2026-11-08T10:00:00Z',
      wordsTargetCount: 3, wordsSelectionNote: '受今日剩余时间预算限制', wordsUnavailableCount: 0,
    }; } },
    contentService: { get: async () => { details++; return {}; } },
  });
  p.data.date = '2026-11-08';
  await p.load();
  assert.equal(reads, 1); assert.equal(details, 0);
  assert.equal(p.data.words[0].meaning, '释义');
  assert.equal(p.data.bookName, '大学英语四级');
  assert.equal(p.data.selectedTime, '11月8日 18:00');
  assert.equal(p.data.targetCount, 3);
  assert.match(p.data.selectionNote, /预算/);
});

test('queue notice distinguishes waiting, unavailable and completed states', () => {
  const { queueNotice } = require('../utils/word-study-entry');
  assert.match(queueNotice({ remaining: 2, current: null, waitSeconds: 90 }), /90 秒/);
  assert.match(queueNotice({ remaining: 0, unavailableCount: 1, allCompleted: false }), /暂不可用/);
  assert.equal(queueNotice({ remaining: 0, allCompleted: true }), '本轮单词已学完');
});

test('a list left open across midnight is treated as history when tapped', async () => {
  let reads = 0;
  const { p, navigation, messages } = page('pages/today/words/index.js', {
    wordStudyService: { queue: async () => { reads++; return selectedQueue; } },
  });
  p.data.date = '2026-11-07'; p.data.isToday = true; p.data.words = fixed.tasks;
  p.openWord({ currentTarget: { dataset: { id: 'selected' } } });
  assert.match(navigation[0], /readonly=1/);
  p.continueStudy(); assert.equal(reads, 0); assert.equal(p.data.isToday, false);
  assert.match(messages[0], /历史词表/);
});

test('late homepage responses cannot overwrite newer completion progress', async () => {
  const pending = [];
  const { p } = page('pages/today/index.js', { dailyTaskService: { day: () => new Promise((resolve) => pending.push(resolve)) } });
  const first = p.load(), second = p.load();
  pending[1]({ active: true, tasks: [{ ...fixed.tasks[0], status: 'DONE' }] }); await second;
  pending[0]({ active: true, tasks: fixed.tasks }); await first;
  assert.equal(p.data.groups[0].done, 1);
});

test('homepage completed-list navigation uses the queue business day after midnight', async () => {
  const { p, navigation } = page('pages/today/index.js', {
    wordStudyService: { queue: async () => ({ businessDate: '2026-11-09', remaining: 0, allCompleted: true }) },
  });
  let refreshes = 0; p.load = () => { refreshes++; };
  await p.openWordGroup(stale);
  assert.equal(navigation[0], '/pages/today/words/index?date=2026-11-09');
  assert.equal(refreshes, 1);
});
