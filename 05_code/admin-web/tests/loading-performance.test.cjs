const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
test('all admin route components load on demand and resolve existing pages', () => {
  const file = fs.readFileSync(path.join(__dirname, '../src/router/index.js'), 'utf8');
  const imports = [...file.matchAll(/const (\w+) = \(\) => import\('([^']+)'\)/g)];
  assert.equal(imports.length, 19);
  for (const [, name, target] of imports) {
    assert.ok(fs.existsSync(path.join(__dirname, '../src/router', target)));
    assert.match(file, new RegExp(`component: ${name}\\b`));
  }
  assert.doesNotMatch(file, /import \w+ from '\.\.\/pages\//);
});
test('import page starts all five independent reads before awaiting results', async () => {
  const file = fs.readFileSync(path.join(__dirname, '../src/pages/imports/index.vue'), 'utf8');
  const code = file.slice(
    file.indexOf('async function loadAll()'),
    file.indexOf('function openNewBatch()'),
  );
  const refs = {};
  for (const name of [
    'loading',
    'error',
    'datasets',
    'books',
    'batches',
    'voiceOptions',
    'onlineBooks',
    'onlinePage',
  ])
    refs[name] = { value: null };
  const calls = [];
  const pending = [];
  const context = {
    ...refs,
    Promise,
    failure: (error) => {
      throw error;
    },
    goOnlinePage() {},
    request: (url) => {
      calls.push(url);
      return new Promise((resolve) => pending.push(() => resolve(url)));
    },
  };
  vm.runInNewContext(code, context);
  const work = context.loadAll();
  assert.equal(calls.length, 5);
  assert.equal(new Set(calls).size, 5);
  pending.forEach((resolve) => resolve());
  await work;
  assert.equal(refs.onlineBooks.value, '/admin/vocabulary/online-books');
  assert.equal(refs.loading.value, false);
});
