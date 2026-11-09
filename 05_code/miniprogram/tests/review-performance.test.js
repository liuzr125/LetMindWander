const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function harness(responses) {
  const clock = { day: '2026-10-08' };
  let page;
  const counts = { review: 0, word: 0, feedback: 0 };
  const rows = ['a', 'b'].map((contentId) => ({
    contentId,
    wordTerm: contentId,
    scheduleId: contentId,
  }));
  vm.runInNewContext(fs.readFileSync(require.resolve('../pages/review/index.js'), 'utf8'), {
    Page: (value) => (page = value),
    require: (name) =>
      name.includes('/date')
        ? { formatBusinessDate: () => clock.day }
        : {
            reviewService: {
              queue: async () => {
                counts.review++;
                return rows;
              },
            },
            wordStudyService: {
              queue: async () => {
                counts.word++;
                return {
                  businessDate: clock.day,
                  remaining: 2,
                  current: { contentId: 'a', version: counts.word },
                };
              },
              feedback: async (id, body) => {
                counts.feedback++;
                return responses.shift()(id, body);
              },
            },
          },
    wx: { showToast() {} },
    Date,
    Math,
    Promise,
  });
  page.setData = (value) => Object.assign(page.data, value);
  return { page, counts, clock };
}
const click = async (page) => {
  page.revealAnswer();
  page.feedback({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
};
test('review uses committed next queue, with no additional reads and exact remaining count', async () => {
  const { page, counts } = harness([
    () => ({
      completed: true,
      remaining: 1,
      businessDate: '2026-10-08',
      current: { contentId: 'b', version: 7 },
    }),
    (id, body) => {
      assert.equal(id, 'b');
      assert.equal(body.expectedVersion, 7);
      return { completed: true, remaining: 0 };
    },
  ]);
  await page.load();
  await click(page);
  assert.equal(page.data.current.contentId, 'b');
  assert.equal(page.data.completed, 1);
  assert.equal(page.data.progress, 50);
  await click(page);
  assert.equal(page.data.current, null);
  assert.equal(page.data.progress, 100);
  assert.deepEqual(counts, { review: 1, word: 1, feedback: 2 });
});
test('old cooldown receipt refreshes once without inventing completion or another write', async () => {
  const { page, counts } = harness([() => ({ completed: false, remaining: 2, waitSeconds: 120 })]);
  await page.load();
  await click(page);
  assert.equal(page.data.current.contentId, 'a');
  assert.equal(page.data.completed, 0);
  assert.equal(page.data.remainingCount, 1);
  assert.equal(page.data.error, '');
  assert.equal(counts.word, 2);
  assert.equal(counts.feedback, 1);
});

test('review requires revealing the answer for each card including the same word repeated', async () => {
  const { page, counts } = harness([() => ({ completed: false, remaining: 2, practiceMode: 'continuous', current: {contentId:'a',version:4} })]);
  await page.load();
  assert.equal(page.data.answerRevealed, false);
  page.feedback({currentTarget:{dataset:{value:'remember'}}});
  assert.equal(counts.feedback, 0);
  await click(page);
  assert.equal(counts.feedback, 1);
  assert.equal(page.data.answerRevealed, false);
  assert.equal(page.data.current.contentId, 'a');
  assert.equal(page.data.completed, 0);
});

test('first onShow does not duplicate load, returning from knowledge refreshes once', async () => {
  const { page, counts } = harness([]);
  const first = page.onLoad({});
  page.onShow();
  await first;
  assert.equal(counts.word, 1);
  await page.onShow();
  assert.equal(counts.word, 2);
});
test('409 discards rejected receipt and refreshes version before next feedback', async () => {
  const writes = [];
  const { page, counts } = harness([
    (id, body) => {
      writes.push(JSON.stringify(body));
      throw Object.assign(Error('conflict'), { statusCode: 409, code: 'WORD_VERSION_CONFLICT' });
    },
    (id, body) => {
      writes.push(JSON.stringify(body));
      assert.equal(body.expectedVersion, 2);
      return { completed: true, remaining: 0 };
    },
  ]);
  await page.load();
  await click(page);
  assert.equal(page.pendingWordAttempt, null);
  assert.equal(counts.word, 2);
  await click(page);
  assert.notEqual(writes[0], writes[1]);
});
test('network uncertainty keeps identical receipt through return and next retry', async () => {
  const writes = [];
  const { page, counts } = harness([
    (id, body) => {
      writes.push(JSON.stringify(body));
      throw Error('network');
    },
    (id, body) => {
      writes.push(JSON.stringify(body));
      return { completed: true, remaining: 0 };
    },
  ]);
  await page.onLoad({});
  page.onShow();
  await click(page);
  await page.onShow();
  assert.equal(counts.word, 1);
  await click(page);
  assert.equal(writes[0], writes[1]);
});
test('Beijing day rollover refreshes before sending an expired version', async () => {
  const { page, counts, clock } = harness([
    (id, body) => {
      assert.equal(body.businessDate, '2026-10-09');
      return { completed: true, remaining: 0 };
    },
  ]);
  await page.load();
  clock.day = '2026-10-09';
  await click(page);
  assert.equal(counts.feedback, 0);
  assert.equal(counts.word, 2);
  await click(page);
  assert.equal(counts.feedback, 1);
});
