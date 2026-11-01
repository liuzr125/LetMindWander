const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/pages/articles/index.vue'), 'utf8');

test('article page loads only article-scoped dependencies', async () => {
  const refs = {};
  for (const name of ['audioBusy', 'selectedIds', 'loading', 'error', 'coverage', 'articles', 'books', 'page'])
    refs[name] = { value: false };
  const calls = [];
  const context = {
    ...refs, Promise, params: () => 'page=1', applyAudioSettings: () => {},
    request: async url => {
      calls.push(url);
      if (url.endsWith('/options')) return { books: [{ bookId: 'book', bookName: '词书' }], articleDifficulties: [] };
      return { items: [], page: 1 };
    },
  };
  const code = source.slice(source.indexOf('async function load()'), source.indexOf('async function ', source.indexOf('async function load()') + 1));
  vm.runInNewContext(code, context);
  await context.load();
  assert.deepEqual(calls, ['/admin/content/articles/options', '/admin/content/articles?page=1']);
  assert.equal(refs.books.value[0].bookId, 'book');
  assert.equal(refs.loading.value, false);
  assert.equal(refs.error.value, '');
});

test('audio readiness reads only the article-scoped boolean status', async () => {
  const calls = [];
  const context = { applyAudioSettings: () => {}, request: async url => { calls.push(url); return { appKeyConfigured: true }; } };
  vm.runInNewContext(source.slice(source.indexOf('async function ensureAudioAppKeyReady()'), source.indexOf('async function openBatchGeneration(')), context);
  await context.ensureAudioAppKeyReady();
  assert.deepEqual(calls, ['/admin/content/articles/tts-status']);
});
