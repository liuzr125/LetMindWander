// The token exists only in the running call, never in progress snapshots or storage.
export async function collectArticleTargets(fetchPage, filters) {
  const snapshot = { ...filters, pageSize: 100 }
  const first = await fetchPage({ ...snapshot, page: 1 })
  const items = [...first.items]
  for (let page = 2; page <= first.totalPages; page++) {
    const next = await fetchPage({ ...snapshot, page })
    items.push(...next.items)
  }
  return [...new Map(items.map(item => [item.contentId, item])).values()]
}

const STOP_CODES = new Set([
  'INVALID_NLS_TEMPORARY_TOKEN', 'TTS_TEMPORARY_TOKEN_REJECTED', 'TTS_TOKEN_FAILED',
  'TTS_APP_KEY_REJECTED', 'TTS_NOT_CONFIGURED', 'MEDIA_STORE_FAILED',
  'APP_PARAMETER_MISSING', 'ADMIN_UNAUTHORIZED'
])

export async function runArticleAudioBatch({ items, nlsToken, generate, shouldStop = () => false, onProgress = () => {}, onSaved = () => {} }) {
  const token = nlsToken.trim()
  if (token.length < 16 || token.length > 4096) throw new Error('请输入有效的临时 NLS Token')
  const unique = [...new Map(items.map(item => [item.contentId, item])).values()]
  const rows = unique.map(item => ({
    contentId: item.contentId, title: item.title,
    status: item.articleAudioUrl ? 'skipped' : 'pending', error: '', code: ''
  }))
  let stopped = false
  const snapshot = () => ({
    items: rows.map(row => ({ ...row })), total: rows.length, stopped,
    done: rows.filter(row => ['succeeded', 'skipped', 'failed'].includes(row.status)).length
  })
  onProgress(snapshot())
  for (const row of rows) {
    if (shouldStop()) { stopped = true; break }
    if (row.status === 'skipped') continue
    row.status = 'running'; onProgress(snapshot())
    let result
    try {
      result = await generate(row.contentId, token)
      if (!result?.audioUrl) throw new Error('接口未返回音频地址，请刷新核对后重试')
    } catch (error) {
      row.status = 'failed'; row.error = error.message || '生成失败'; row.code = error.code || ''
      // Stop on ambiguous transport/server failures and shared credential/storage problems.
      stopped = STOP_CODES.has(error.code) || !error.status || error.status === 401 ||
        error.status === 403 || error.status === 429 || error.status >= 500
      onProgress(snapshot())
      if (stopped) break
      continue
    }
    row.status = result.cached ? 'skipped' : 'succeeded'
    onSaved(row.contentId, result)
    onProgress(snapshot())
  }
  if (shouldStop() && rows.some(row => row.status === 'pending')) stopped = true
  const final = snapshot(); onProgress(final)
  return final
}
