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
      ? { formatBusinessDate: () => '2026-11-08' } : services,
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
const fixed = { tasks: [{ taskType: 'word', id: 'new-task', contentId: 'selected', status: 'TODO', versionNo: 3 }] };

test('first click waits for persisted selection and concurrent taps share the request', async () => {
  let finish, calls = 0;
  const { p, navigation } = page('pages/today/index.js', {
    dailyTaskService: { selectWords: (date) => {
      assert.equal(date, '2026-11-08'); calls++;
      return new Promise((resolve) => { finish = resolve; });
    } },
  });
  const one = p.openWordGroup(stale), two = p.openWordGroup(stale);
  assert.equal(one, two);
  assert.equal(calls, 1);
  assert.equal(navigation.length, 0);
  finish(fixed);
  await one;
  assert.match(navigation[0], /id=selected&taskId=new-task&version=3&daily=1$/);
});

test('failed selection does not navigate to provisional words and can be retried', async () => {
  let fail = true;
  const { p, navigation, messages } = page('pages/today/index.js', {
    dailyTaskService: { selectWords: async () => { if (fail) throw Error('网络失败'); return fixed; } },
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
    dailyTaskService: { selectWords: async () => ({ tasks: [{ ...fixed.tasks[0], status: 'DONE' }] }) },
  });
  await p.openWordGroup(stale);
  assert.equal(navigation[0], '/pages/today/words/index?date=2026-11-08');
});

test('today list reuses selection while past date lists only read history', async () => {
  const calls = [];
  const { p } = page('pages/today/words/index.js', {
    dailyTaskService: {
      selectWords: async (date) => { calls.push(['select', date]); return fixed; },
      day: async (date) => { calls.push(['read', date]); return fixed; },
    },
    contentService: { get: async () => ({ meaning: '释义' }) },
  });
  p.data.date = '2026-11-08';
  await p.load();
  await p.load();
  p.data.date = '2026-11-07';
  await p.load();
  assert.deepEqual(calls, [['select','2026-11-08'],['select','2026-11-08'],['read','2026-11-07']]);
  assert.equal(p.data.words[0].contentId, 'selected');
});
