const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

async function loadGroups(tasks, gaps = []) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/today/index.js'), 'utf8'), {
    require: name => name.includes('utils/date')
      ? { formatBusinessDate: () => '2026-10-08', formatBusinessChineseDate: () => '10月8日' }
      : { dailyTaskService: { day: async () => ({ active: true, tasks, gaps }) } },
    Page: value => { definition = value; }, Date
  });
  const page = { ...definition, data: { ...definition.data }, setData(value) { Object.assign(this.data, value); } };
  page.load();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.data.error, '');
  return page.data.groups;
}

test('zero, partial and full progress use distinct status text', async () => {
  for (const type of ['tech', 'word', 'review', 'journal', 'action']) {
    const tasks = [{ taskType: type, status: 'TODO' }, { taskType: type, status: 'TODO' }];
    assert.equal((await loadGroups(tasks))[0].statusText, '未开始 0/2');
    tasks[0].status = 'DONE';
    assert.equal((await loadGroups(tasks))[0].statusText, '进行中 1/2');
    tasks[1].status = 'DONE';
    assert.equal((await loadGroups(tasks))[0].statusText, '已完成');
  }
});

test('cancelled tasks are excluded and missing wordbook keeps its prompt', async () => {
  const groups = await loadGroups([{ taskType: 'tech', status: 'DONE' }, { taskType: 'tech', status: 'CANCELLED' }],
    [{ taskType: 'word', reason: '未选择词书', gapCount: 5 }]);
  assert.equal(groups[0].statusText, '已完成');
  assert.equal(groups[0].total, 1);
  assert.equal(groups[1].statusText, '请先选择学习词书');
});
