const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function page(service, extras = {}) {
  let p;
  const navigation = [];
  const timers = [];
  vm.runInNewContext(fs.readFileSync(require.resolve('../pages/word/detail/index.js'), 'utf8'), {
    Page: (v) => (p = v),
    require: (n) =>
      n.includes('word-study-preferences')
        ? require('../utils/word-study-preferences')
        : n.includes('word-learning-cards')
          ? require('../utils/word-learning-cards')
          : n.includes('word-study-entry')
            ? require('../utils/word-study-entry')
          : n.includes('/date')
            ? { formatBusinessDate: () => '2026-10-08' }
            : Object.assign({ wordStudyService: service }, extras),
    wx: {
      redirectTo: (v) => navigation.push(v.url),
      pageScrollTo: () => {},
      showToast: () => {},
      navigateBack: () => {},
    },
    Date,
    Math,
    Promise,
    setTimeout: () => { timers.push(true); },
    clearTimeout,
  });
  p.setData = (v) => Object.assign(p.data, v);
  p.data.contentId = 'word';
  p.load = async () => {};
  return { p, navigation, timers };
}
const queue = { businessDate: '2026-10-08', current: { contentId: 'word', version: 3 } };
test('original feedback buttons save consolidation and advance to queued word', async () => {
  const writes = [];
  const { p, navigation } = page({
    queue: async () => queue,
    feedback: async (id, body) => {
      writes.push(body);
      return { current: { contentId: 'next' } };
    },
  });
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(writes[0].expectedVersion, 3);
  assert.equal(writes[0].feedback, 'remember');
  assert.equal(p.data.contentId, 'next');
  assert.equal(navigation.length, 0);
});
test('daily word mode persists through feedback and advancing to another selected word', async () => {
  const modes = [], writes = [];
  const { p } = page({
    queue: async (mode) => { modes.push(mode); return queue; },
    feedback: async (id, body) => {
      writes.push(body);
      return { businessDate: '2026-10-08', current: { contentId: 'next', version: 2 } };
    },
  });
  p.dailyWordMode = true;
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(modes[0], 'daily');
  assert.equal(writes[0].mode, 'daily');
  assert.equal(p.dailyWordMode, true);
  assert.equal(p.data.contentId, 'next');
});
test('resuming daily study displays the server-ready word instead of a waiting preferred word', async () => {
  const { p } = page({ queue: async () => ({ businessDate: '2026-10-08', current: { contentId: 'ready-next', version: 2 } }) });
  p.dailyWordMode = true;
  p.loadWordQueue = () => {};
  await p.resumeStudy();
  assert.equal(p.data.contentId, 'ready-next');
  assert.equal(p.data.studyPaused, false);
});
test('blocked words after feedback keep an explanation and never auto-navigate as completed', async () => {
  const { p, timers } = page({
    queue: async () => queue,
    feedback: async () => ({ current: null, remaining: 0, unavailableCount: 1, unfinishedCount: 1, allCompleted: false }),
  });
  p.dailyWordMode = true;
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.match(p.data.studyNotice, /暂不可用/);
  assert.equal(p.data.studyPaused, true);
  assert.equal(timers.length, 0);
});
test('failed request retries identical receipt even when another button is tapped', async () => {
  const writes = [];
  let n = 0;
  const { p } = page({
    queue: async () => queue,
    feedback: async (id, body) => {
      writes.push(JSON.stringify(body));
      if (!n++) throw Error('network');
      return { remaining: 1 };
    },
  });
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  p.rateWord({ currentTarget: { dataset: { value: 'unclear' } } });
  await new Promise(setImmediate);
  assert.equal(writes[0], writes[1]);
  assert.equal(p.pendingWordAttempt, null);
});
test('spacing wait never creates an additional feedback', async () => {
  let writes = 0;
  const { p } = page({ queue: async () => ({ remaining: 1 }), feedback: async () => writes++ });
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(writes, 0);
});
test('word business day uses Beijing midnight', () => {
  const { formatBusinessDate } = require('../utils/date');
  assert.equal(formatBusinessDate(new Date('2026-10-08T16:00:00Z')), '2026-10-09');
});
test('review retains original card and buttons while saving a word receipt', async () => {
  let p;
  const writes = [];
  let pending = true;
  vm.runInNewContext(fs.readFileSync(require.resolve('../pages/review/index.js'), 'utf8'), {
    Page: (v) => (p = v),
    require: (n) =>
      n.includes('/date')
        ? { formatBusinessDate: () => '' }
        : {
            reviewService: {
              queue: async () =>
                pending ? [{ wordTerm: 'word', contentId: 'word', scheduleId: 's' }] : [],
            },
            wordStudyService: {
              queue: async () => (pending ? queue : { remaining: 0 }),
              feedback: async (id, body) => {
                writes.push(body);
                pending = false;
              },
            },
          },
    wx: { showToast: () => {} },
    Date,
    Math,
    Promise,
  });
  p.setData = (v) => Object.assign(p.data, v);
  await p.load();
  assert.equal(p.data.current.wordTerm, 'word');
  p.feedback({ currentTarget: { dataset: { value: 'fuzzy' } } });
  await new Promise(setImmediate);
  assert.equal(writes[0].mode, 'review');
  assert.equal(writes[0].feedback, 'fuzzy');
  assert.equal(p.data.current, null);
});
test('original word feedback area maps 模糊 to fuzzy', async () => {
  const markup = fs.readFileSync(require.resolve('../pages/word/detail/index.wxml'), 'utf8');
  assert.match(
    markup,
    /<button\s+class="fuzzy"\s+data-value="fuzzy"\s+bindtap="rateWord"[^>]*>模糊<\/button\s*>/,
  );
  const writes = [];
  const { p } = page({
    queue: async () => queue,
    feedback: async (id, body) => {
      writes.push(body);
      return { remaining: 1 };
    },
  });
  p.rateWord({ currentTarget: { dataset: { value: 'fuzzy' } } });
  await new Promise(setImmediate);
  assert.equal(writes[0].feedback, 'fuzzy');
  assert.equal(writes[0].expectedVersion, 3);
});

test('successive words reuse returned queue version without another queue request or page redirect', async () => {
  let queues = 0;
  const writes = [];
  const { p, navigation } = page({
    queue: async () => {
      queues++;
      return queue;
    },
    feedback: async (id, body) => {
      writes.push({ id, body });
      return writes.length === 1
        ? { businessDate: '2026-10-08', current: { contentId: 'next', version: 7 } }
        : { remaining: 0 };
    },
  });
  await p.prepareStudyQueue();
  p.rateWord({ currentTarget: { dataset: { value: 'fuzzy' } } });
  await new Promise(setImmediate);
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(queues, 1);
  assert.equal(writes.length, 2);
  assert.equal(writes[1].id, 'next');
  assert.equal(writes[1].body.expectedVersion, 7);
  assert.equal(navigation.length, 0);
});
test('expired business day refreshes queue instead of submitting stale version', async () => {
  let queues = 0;
  const writes = [];
  const { p } = page({
    queue: async () => {
      queues++;
      return queue;
    },
    feedback: async (id, body) => {
      writes.push(body);
      return { remaining: 0 };
    },
  });
  p.studyQueue = { businessDate: '2026-10-07', current: { contentId: 'word', version: 1 } };
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(queues, 1);
  assert.equal(writes[0].expectedVersion, 3);
});

test('server version conflict refreshes queue on retry while network uncertainty preserves receipt', async () => {
  let queues = 0,
    writes = 0;
  const { p } = page({
    queue: async () => {
      queues++;
      return queue;
    },
    feedback: async () => {
      if (!writes++) {
        const e = Error('stale');
        e.statusCode = 409;
        throw e;
      }
      return { remaining: 0 };
    },
  });
  await p.prepareStudyQueue();
  p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(p.pendingWordAttempt, null);
  assert.equal(p.studyQueue, null);
  p.rateWord({ currentTarget: { dataset: { value: 'fuzzy' } } });
  await new Promise(setImmediate);
  assert.equal(queues, 2);
  assert.equal(writes, 2);
});

test('unclear feedback advances in place and stops old audio', async () => {
  const writes = [];
  const { p } = page({
    queue: async () => queue,
    feedback: async (id, body) => {
      writes.push(body);
      return { businessDate: '2026-10-08', current: { contentId: 'next', version: 2 } };
    },
  });
  let stopped = 0;
  p.stopAudio = () => stopped++;
  p.rateWord({ currentTarget: { dataset: { value: 'unclear' } } });
  await new Promise(setImmediate);
  assert.equal(writes[0].feedback, 'unclear');
  assert.equal(stopped, 1);
  assert.equal(p.data.contentId, 'next');
});
test('late previous-word cards and memory hints cannot replace next-word supplements', async () => {
  let resolveCards, resolveHints;
  const { p } = page(
    {},
    {
      contentService: {
        cardPreferences: async () => ({ types: [] }),
        learningCards: () => new Promise((resolve) => (resolveCards = resolve)),
      },
      wordMemoryService: {
        config: async () => ({ enabled: true }),
        hints: () => new Promise((resolve) => (resolveHints = resolve)),
      },
    },
  );
  p.loadLearningCards();
  p.loadMemory();
  await new Promise(setImmediate);
  p.data.contentId = 'next';
  p.data.learningCardGroups = ['next-card'];
  p.data.memoryHints = ['next-hint'];
  resolveCards([]);
  resolveHints(['old-hint']);
  await new Promise(setImmediate);
  assert.deepEqual(p.data.learningCardGroups, ['next-card']);
  assert.deepEqual(p.data.memoryHints, ['next-hint']);
});

test('refresh after uncertain feedback retries the original word and receipt before switching', async () => {
  const writes = []; let queues = 0;
  const { p } = page({
    queue: async () => { queues++; return queue; },
    feedback: async (id, body) => {
      writes.push({ id, body: JSON.stringify(body) });
      if (writes.length === 1) throw Error('network');
      return { businessDate: '2026-10-08', current: { contentId: 'next', version: 4 } };
    },
  });
  p.dailyWordMode = true;
  await p.rateWord({ currentTarget: { dataset: { value: 'fuzzy' } } });
  p.data.contentId = 'another-view'; p.data.recallActive = true;
  await p.resumeStudy();
  assert.deepEqual(writes[1], writes[0]);
  assert.equal(writes[1].id, 'word'); assert.equal(queues, 1);
  assert.equal(p.data.contentId, 'next'); assert.equal(p.pendingWordAttempt, null);
});

test('refresh and repeated feedback during an in-flight write share its promise', async () => {
  let finish, writes = 0;
  const { p } = page({
    queue: async () => queue,
    feedback: () => { writes++; return new Promise((resolve) => { finish = resolve; }); },
  });
  const first = p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  await new Promise(setImmediate);
  assert.equal(p.resumeStudy(), first);
  assert.equal(p.rateWord({ currentTarget: { dataset: { value: 'unclear' } } }), first);
  finish({ businessDate: '2026-10-08', current: { contentId: 'next', version: 4 } });
  await first; assert.equal(writes, 1); assert.equal(p.data.contentId, 'next');
});

test('duplicate resume reads are merged and restricted study skips a redundant task-package read', async () => {
  let finish, reads = 0, loads = 0;
  const { p } = page({ queue: () => { reads++; return new Promise((resolve) => { finish = resolve; }); } });
  p.dailyWordMode = true;
  p.loadWordQueue = () => { throw Error('redundant task-package request'); };
  p.load = async () => { loads++; };
  const first = p.resumeStudy(); assert.equal(p.resumeStudy(), first);
  assert.equal(p.rateWord({ currentTarget: { dataset: { value: 'remember' } } }), first);
  finish(queue); await first;
  assert.equal(reads, 1); assert.equal(loads, 1);
});

test('a confirmed old-day receipt is followed by a fresh queue instead of replaying yesterday next word', async () => {
  let reads = 0; const writes = [];
  const { p } = page({
    queue: async () => { reads++; return { businessDate: '2026-10-08', current: { contentId: 'today', version: 7 } }; },
    feedback: async (id, body) => { writes.push({ id, day: body.businessDate }); return { businessDate: '2026-10-07', current: { contentId: 'yesterday', version: 2 } }; },
  });
  p.dailyWordMode = true;
  p.pendingWordAttempt = { attemptId: 'uncertain', feedback: 'remember', expectedVersion: 1, businessDate: '2026-10-07', mode: 'daily' };
  p.pendingWordContentId = 'original';
  await p.resumeStudy();
  assert.equal(reads, 1); assert.equal(writes[0].id, 'original');
  assert.equal(writes[0].day, '2026-10-07'); assert.equal(p.data.contentId, 'today');
});

test('changed restricted queue switches the displayed word but requires a new feedback decision', async () => {
  const writes = []; let reads = 0;
  const { p } = page({
    queue: async () => { reads++; return { businessDate: '2026-10-08', current: { contentId: 'changed', version: 6 } }; },
    feedback: async (id, body) => { writes.push({ id, feedback: body.feedback }); return { remaining: 0 }; },
  });
  p.dailyWordMode = true;
  await p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  assert.equal(p.data.contentId, 'changed'); assert.equal(writes.length, 0);
  await p.rateWord({ currentTarget: { dataset: { value: 'fuzzy' } } });
  assert.equal(writes[0].id, 'changed'); assert.equal(writes[0].feedback, 'fuzzy');
  assert.equal(reads, 1);
});

test('a failed preflight queue read does not immediately duplicate the request or submit feedback', async () => {
  let reads = 0, writes = 0;
  const { p } = page({
    queue: async () => { reads++; throw Error('network'); },
    feedback: async () => { writes++; },
  });
  await p.rateWord({ currentTarget: { dataset: { value: 'remember' } } });
  assert.equal(reads, 1); assert.equal(writes, 0); assert.equal(p.data.acting, false);
});
