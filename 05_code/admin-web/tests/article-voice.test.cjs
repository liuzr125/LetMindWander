const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/pages/articles/index.vue'), 'utf8');
const code = source.slice(source.indexOf('function applyAudioSettings('), source.indexOf('async function openBatchGeneration('));

function setup(request) {
  const context = { Array, request };
  for (const name of ['articleVoice', 'savedArticleVoice']) context[name] = { value: 'luca' };
  for (const name of ['voiceSaving', 'audioBusy']) context[name] = { value: false };
  for (const name of ['voiceMessage', 'voiceError']) context[name] = { value: '' };
  context.articleVoices = { value: [] };
  context.savedVoiceLabel = { get value() { return context.savedArticleVoice.value; } };
  vm.runInNewContext(code, context);
  return context;
}

test('saves the chosen article voice and uses the persisted value after reload', async () => {
  const calls = [];
  const context = setup(async (url, options) => {
    calls.push({ url, options });
    return { voice: 'luna', voices: [{ code: 'luca', label: 'Luca' }, { code: 'luna', label: 'Luna' }] };
  });
  context.articleVoice.value = 'luna';
  await context.saveArticleVoice();
  assert.equal(calls[0].url, '/admin/content/articles/tts-settings');
  assert.equal(calls[0].options.body, '{"voice":"luna"}');
  assert.equal(context.savedArticleVoice.value, 'luna');
  assert.equal(context.voiceSaving.value, false);
  assert.match(context.voiceMessage.value, /已有音频不变/);
  const reloaded = setup();
  reloaded.applyAudioSettings({ voice: context.savedArticleVoice.value, voices: context.articleVoices.value });
  assert.equal(reloaded.articleVoice.value, 'luna');
});

test('rejects duplicate saves and changes while audio generation is busy', async () => {
  let calls = 0;
  const context = setup(async () => { calls++; });
  await context.saveArticleVoice();
  context.articleVoice.value = 'luna';
  context.audioBusy.value = true;
  await context.saveArticleVoice();
  assert.equal(calls, 0);
});

test('failed save keeps the last saved default and shows an error', async () => {
  const context = setup(async () => { throw new Error('音色无效'); });
  context.articleVoice.value = 'invalid';
  await context.saveArticleVoice();
  assert.equal(context.savedArticleVoice.value, 'luca');
  assert.equal(context.voiceError.value, '音色无效');
  assert.equal(context.voiceSaving.value, false);
});
