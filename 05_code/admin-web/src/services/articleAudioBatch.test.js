import test from 'node:test'
import assert from 'node:assert/strict'
import { collectArticleTargets, runArticleAudioBatch } from './articleAudioBatch.js'

const token = 'fake-temporary-token-for-tests'
const items = [{ contentId: 'a', title: 'A' }, { contentId: 'b', title: 'B' }, { contentId: 'c', title: 'C' }]

test('collects every page with frozen filters and deduplicates', async () => {
  const queries = []
  const targets = await collectArticleTargets(async query => {
    queries.push(query)
    return query.page === 1 ? { items: items.slice(0, 2), totalPages: 2 } : { items: items.slice(1), totalPages: 2 }
  }, { difficulty: 'intro', keyword: 'Library' })
  assert.deepEqual(targets, items)
  assert.deepEqual(queries.map(q => q.page), [1, 2])
  assert.ok(queries.every(q => q.difficulty === 'intro' && q.keyword === 'Library' && q.pageSize === 100))
})

test('one token is reused sequentially; existing audio is skipped; progress has no token', async () => {
  const calls = [], saved = [], progress = []
  let inFlight = 0
  const result = await runArticleAudioBatch({
    items: [{ ...items[0], articleAudioUrl: '/existing' }, ...items.slice(1), items[1]], nlsToken: token,
    generate: async (id, usedToken) => {
      assert.equal(++inFlight, 1); assert.equal(usedToken, token)
      calls.push(id); await Promise.resolve(); inFlight--
      return { audioUrl: '/audio/' + id, assetId: id }
    },
    onSaved: (id, audio) => saved.push([id, audio.assetId]),
    onProgress: state => progress.push(state)
  })
  assert.deepEqual(calls, ['b', 'c'])
  assert.deepEqual(saved, [['b', 'b'], ['c', 'c']])
  assert.deepEqual(result.items.map(i => i.status), ['skipped', 'succeeded', 'succeeded'])
  assert.equal(result.done, 3)
  assert.ok(!JSON.stringify(progress).includes(token))
})

test('expired token stops remaining requests and keeps completed work', async () => {
  const calls = []
  const result = await runArticleAudioBatch({ items, nlsToken: token, generate: async id => {
    calls.push(id)
    if (id === 'b') throw Object.assign(new Error('Token expired'), { code: 'TTS_TEMPORARY_TOKEN_REJECTED', status: 502 })
    return { audioUrl: '/audio/' + id }
  } })
  assert.deepEqual(calls, ['a', 'b'])
  assert.deepEqual(result.items.map(i => i.status), ['succeeded', 'failed', 'pending'])
  assert.equal(result.stopped, true)
})

test('per-article validation failure does not block the next article', async () => {
  const result = await runArticleAudioBatch({ items, nlsToken: token, generate: async id => {
    if (id === 'b') throw Object.assign(new Error('Too long'), { code: 'TTS_TEXT_TOO_LONG', status: 400 })
    return { audioUrl: '/audio/' + id }
  } })
  assert.deepEqual(result.items.map(i => i.status), ['succeeded', 'failed', 'succeeded'])
  assert.equal(result.stopped, false)
})

test('stop waits for current save and does not send the next request', async () => {
  let stop = false
  const calls = []
  const result = await runArticleAudioBatch({ items, nlsToken: token, shouldStop: () => stop, generate: async id => {
    calls.push(id); stop = true; return { audioUrl: '/audio/' + id }
  } })
  assert.deepEqual(calls, ['a'])
  assert.deepEqual(result.items.map(i => i.status), ['succeeded', 'pending', 'pending'])
})

test('retry submits only failed and pending items; cached response counts as skipped', async () => {
  const calls = []
  const result = await runArticleAudioBatch({ items: items.slice(1), nlsToken: token, generate: async id => {
    calls.push(id); return { audioUrl: '/audio/' + id, cached: true }
  } })
  assert.deepEqual(calls, ['b', 'c'])
  assert.deepEqual(result.items.map(i => i.status), ['skipped', 'skipped'])
})

test('transport failure stops queue and blank token sends no requests', async () => {
  let calls = 0
  const generate = async () => { calls++; throw new TypeError('Network unavailable') }
  const result = await runArticleAudioBatch({ items, nlsToken: token, generate })
  assert.equal(calls, 1); assert.equal(result.stopped, true)
  await assert.rejects(runArticleAudioBatch({ items, nlsToken: ' ', generate }))
  assert.equal(calls, 1)
})
