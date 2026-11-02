const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/pages/jobs/index.vue'), 'utf8');

test('collection history separates actual inserts, duplicate skips and daily shortfall', () => {
  const context = {
    runs: { value: [{ id: 'new', fetchedCount: 34, insertedCount: 10, skippedCount: 34, remainingCount: 0 }, { id: 'old', fetchedCount: 34, insertedCount: 0, skippedCount: 34 }] },
  };
  vm.runInNewContext(source.slice(source.indexOf('function historyFor('), source.indexOf('async function load(')), context);
  const rows = context.historyFor('collection');
  assert.equal(rows[0].succeeded, 10);
  assert.equal(rows[0].skipped, 34);
  assert.equal(rows[0].failed, 0);
  assert.equal(rows[1].failed, '—');
});

test('manual collection warns about AI cost and cancellation never submits a job', async () => {
  const requests = [];
  const context = {
    runningTasks: { value: {} }, savingStateTasks: { value: {} },
    window: { confirm: message => { assert.match(message, /10 条/); assert.match(message, /AI/); return false; } },
    request: async (...args) => { requests.push(args); },
  };
  vm.runInNewContext(source.slice(source.indexOf('async function runTask('), source.indexOf('async function toggleTask(')), context);
  await context.runTask({ code: 'collection', enabled: true });
  assert.equal(requests.length, 0);
});

test('historical runs without a target snapshot do not receive a fabricated daily quota', () => {
  assert.match(source, /v-if="runDetail\.run\.targetCount != null"/);
  assert.match(source, /本次 AI 新增/);
  assert.match(source, /今日缺口/);
});
