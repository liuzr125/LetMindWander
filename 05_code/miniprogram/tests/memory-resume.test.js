const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { createStore } = require('../utils/word-memory-pending');

function storageFixture() {
  const data = new Map();
  const context = { owner: 'owner-a', time: Date.now() };
  const store = createStore({ getOwner: () => context.owner, now: () => Math.max(context.time,Date.now()),
    storage: { get: key => data.get(key), set: (key, value) => data.set(key, JSON.parse(JSON.stringify(value))), remove: key => data.delete(key) } });
  return { data, context, store };
}
function page(service, store) {
  let p;
  const navigation = [];
  vm.runInNewContext(fs.readFileSync(require.resolve('../pages/word/memory/session/index.js'), 'utf8'), {
    Page: value => { p = value; },
    require: name => name.includes('word-memory-pending') ? store : { wordMemoryService: service },
    wx: { showToast() {}, showModal: () => { throw Error('must resolve pending write before finishing'); }, redirectTo: value => navigation.push(value.url) }, Date, Promise, Math,
  });
  p.setData = value => {
    for (const [key, item] of Object.entries(value)) {
      const parts = key.split('.'); let target = p.data;
      while (parts.length > 1) { const name = parts.shift(); target[name] = target[name] || {}; target = target[name]; }
      target[parts[0]] = item;
    }
  };
  return { p, navigation };
}
const episode = id => ({ id, dimension: 'meaning', attemptCount: 0, availableHints: [] });
const session = current => ({ state: 'active', version: 1, targetCount: 1, currentEpisode: episode(current) });

test('uncertain answer is immutable and replays the exact original receipt after page reconstruction', async () => {
  const { store } = storageFixture();
  const writes = []; let reads = 0, finishes = 0;
  const service = {
    getSession: async () => { reads++; return session(writes.length > 1 ? 'next' : 'e'); },
    attempt: async (sid, eid, payload, key) => {
      writes.push(JSON.stringify({ sid, eid, payload, key }));
      if (writes.length === 1) throw Error('response lost');
      return { episodeId: 'e', attemptNo: 1, sessionVersion: 2, result: 'incorrect', canRetry: true };
    },
    finish: async () => { finishes++; },
  };
  const one = page(service, store).p;
  await one.onLoad({ id: 'session' }); one.onShow();
  one.answerChanged({ detail: { value: 'original answer' } });
  await one.submit();
  assert.equal(one.data.pendingSave, true);
  one.answerChanged({ detail: { value: 'changed answer' } });
  one.next(); one.finishPartial();
  assert.equal(one.data.answer, 'original answer');
  assert.equal(finishes, 0);
  assert.equal(reads, 1);
  const two = page(service, store).p;
  await two.onLoad({ id: 'session' });
  assert.equal(writes[0], writes[1]);
  assert.equal(store.load('session'), null);
  assert.equal(two.data.pendingSave, false);
  assert.equal(two.data.current.id, 'next');
});

test('version rejection drops pending receipt, refreshes once and clamps background duration', async () => {
  const { store } = storageFixture(); let reads = 0, payload;
  const p = page({
    getSession: async () => { reads++; return session(reads === 1 ? 'old' : 'new'); },
    attempt: async (sid, eid, body) => { payload = body; throw Object.assign(Error('updated'), {statusCode:409,code:'MEMORY_EPISODE_CLOSED'}); },
  }, store).p;
  await p.onLoad({id:'s'});
  p.startedAt = Date.now() - 7200000;
  p.answerChanged({detail:{value:'answer'}});
  await p.submit();
  assert.equal(payload.durationMs,3600000);
  assert.equal(store.load('s'),null);
  assert.equal(p.data.current.id,'new');
  assert.equal(p.data.answer,'');
  assert.equal(reads,2);
});

test('a version refresh preserves the typed answer only when the server still presents that episode', async () => {
  const {store}=storageFixture();let reads=0;
  const p=page({getSession:async()=>({...session('same'),version:++reads}),
    attempt:async()=>{throw Object.assign(Error('version changed'),{statusCode:409});}},store).p;
  await p.onLoad({id:'s'});
  p.answerChanged({detail:{value:'typed answer'}});
  await p.submit();
  assert.equal(p.data.answer,'typed answer');
  assert.equal(p.data.session.version,2);
  assert.equal(p.data.pendingSave,false);
});

test('a rejected restored receipt exits loading and exposes the missing-session error', async () => {
  const {store,context}=storageFixture();
  store.save({sessionId:'s',episodeId:'e',key:'attempt',createdAt:context.time,payload:{answer:'a',durationMs:1,expectedVersion:1}});
  const p=page({attempt:async()=>{throw Object.assign(Error('missing session'),{statusCode:404});}},store).p;
  await p.onLoad({id:'s'});
  assert.equal(p.data.loading,false);
  assert.equal(p.data.pendingSave,false);
  assert.equal(p.data.error,'missing session');
  assert.equal(store.load('s'),null);
});

test('pending storage is scoped by owner and session, expires, and cannot clear a newer receipt', () => {
  const { store, context, data } = storageFixture();
  const attempt={sessionId:'s',episodeId:'e',key:'attempt-1',createdAt:context.time,payload:{answer:'a',durationMs:1,expectedVersion:1}};
  assert.equal(store.save(attempt),true);
  context.owner='owner-b';assert.equal(store.load('s'),null);
  context.owner='owner-a';assert.equal(store.load('other'),null);
  store.clear('s','different');assert.equal(store.load('s').key,'attempt-1');
  store.save({...attempt,key:'attempt-2'});store.clear('s','attempt-1');
  assert.equal(store.load('s').key,'attempt-2');
  context.time+=8*86400000;
  assert.equal(store.load('s'),null);assert.equal(data.size,0);
});
