const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function harness(responses, options = {}) {
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
              snapshot: async (date) => {
                counts.review++;
                if(options.readSnapshot) return options.readSnapshot(date);
                return {businessDate:options.snapshotDate || clock.day,items:options.rows || rows,completedCount:options.completedCount || 0};
              },
            },
            wordStudyService: {
              queue: async () => {
                counts.word++;
                return options.study || {
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

test('unavailable word reviews stay pending even with missing live word text', async () => {
  const options={rows:[{contentId:'a',wordReview:true,available:false,title:'unavailable'}],
    completedCount:2,study:{remaining:0,current:null,businessDate:'2026-10-08'}};
  const {page,counts}=harness([],options);
  await page.load();
  assert.equal(page.data.current,null);
  assert.equal(page.data.pendingCount,1);
  assert.equal(page.data.blockedCount,1);
  assert.equal(page.data.completed,2);
  assert.equal(page.data.total,3);
  assert.equal(page.data.progress,67);
  assert.equal(page.data.error,'');
  page.feedback({currentTarget:{dataset:{value:'remember'}}});
  assert.equal(counts.feedback,0);
  options.rows[0].available=true;
  options.study.current={contentId:'a',version:2};options.study.remaining=1;
  await page.load();
  assert.equal(page.data.current.wordReview,true);
  assert.equal(page.data.answerRevealed,false);
  assert.equal(page.data.completed,2);
});

test('reloading reviews restores completed count from server facts and rolls explicit dates to today', async () => {
  const options={};
  const {page,clock}=harness([()=>({completed:true,remaining:1,current:{contentId:'b',version:3}})],options);
  await page.onLoad({date:'2026-10-01'});
  assert.equal(page.data.date,'2026-10-08');
  await click(page);
  options.completedCount=1;
  options.rows=[{contentId:'b',wordTerm:'b'}];
  options.study={remaining:1,current:{contentId:'b',version:3},businessDate:'2026-10-08'};
  await page.load();
  assert.equal(page.data.completed,1);
  assert.equal(page.data.progress,50);
  clock.day='2026-10-09';options.completedCount=0;options.rows=[];
  options.study={remaining:0,current:null,businessDate:clock.day};
  await page.load();
  assert.equal(page.data.date,'2026-10-09');
  assert.equal(page.data.completed,0);
  assert.equal(page.data.progress,0);
});

test('midnight during a read refreshes once, and a persistent date mismatch stops instead of looping', async () => {
  let resolve;
  const options={study:{businessDate:'2026-10-09',remaining:0,current:null},readSnapshot:date=>date==='2026-10-08'?new Promise(done=>{resolve=done;}):Promise.resolve({businessDate:date,items:[],completedCount:0})};
  const {page,clock,counts}=harness([],options);
  const pending=page.load();
  assert.equal(page.load(),pending);
  clock.day='2026-10-09';
  resolve({businessDate:'2026-10-08',items:[],completedCount:1});
  await pending;
  assert.equal(page.data.date,'2026-10-09');
  assert.equal(page.data.error,'');
  assert.equal(counts.review,2);
  delete options.readSnapshot;options.snapshotDate='2026-10-08';
  await page.load();
  assert.match(page.data.error,/日期/);
  assert.equal(counts.review,4);
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
