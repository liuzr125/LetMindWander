<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { collectArticleTargets, runArticleAudioBatch } from '../../services/articleAudioBatch.js'
import { request } from '../../services/request.js'
import { showError } from '../../services/notification.js'

const loading = ref(true), listLoading = ref(false), error = ref('')
const coverage = ref({ articleDifficulties: [] })
const books = ref([])
const audioTarget = ref(null), nlsToken = ref(''), audioError = ref(''), audioSuccess = ref('')
const articles = ref({ total: 0, page: 1, pageSize: 20, totalPages: 0, items: [] })
const page = ref(1), pageSize = ref(20), difficulty = ref(''), bookId = ref(''), keywordInput = ref(''), keyword = ref('')
const detailOpen = ref(false), detailLoading = ref(false), detailError = ref(''), detail = ref(null)
const generatingArticleId = ref('')
let currentAudio = null
const selectedIds = ref([]), batchPreparing = ref(false), batchRunning = ref(false)
const batchProgress = ref(null), batchStopRequested = ref(false)
const audioBusy = computed(() => !!generatingArticleId.value || batchRunning.value || batchPreparing.value)
const selectableItems = computed(() => articles.value.items.filter(item => !item.articleAudioUrl))
const allPageSelected = computed(() => selectableItems.value.length > 0 && selectableItems.value.every(item => selectedIds.value.includes(item.contentId)))
const retryItems = computed(() => (batchProgress.value?.items || []).filter(item => ['failed', 'pending'].includes(item.status)))
const batchCounts = computed(() => {
  const rows = batchProgress.value?.items || []
  return Object.fromEntries(['succeeded', 'skipped', 'failed', 'pending'].map(status => [status, rows.filter(row => row.status === status).length]))
})
function selectPage(checked) { selectedIds.value = checked ? selectableItems.value.map(item => item.contentId) : [] }
function batchStatus(status) { return ({ pending: '未处理', running: '生成并保存中', succeeded: '已保存', skipped: '已有音频，已跳过', failed: '失败' })[status] }
function updateSavedAudio(id, result) {
  const item = articles.value.items.find(item => item.contentId === id)
  if (item) { item.articleAudioUrl = result.audioUrl; item.articleAudioAssetId = result.assetId }
  if (detail.value?.contentId === id) detail.value.articleAudioUrl = result.audioUrl
  selectedIds.value = selectedIds.value.filter(value => value !== id)
}
async function openBatchGeneration(scope) {
  if (audioBusy.value) return
  audioSuccess.value = ''; audioError.value = ''; nlsToken.value = ''
  batchPreparing.value = true
  try {
    let items
    if (scope === 'all') {
      const filters = { difficulty: difficulty.value, bookId: bookId.value, keyword: keyword.value }
      items = await collectArticleTargets(query => {
        const values = new URLSearchParams(Object.entries(query).filter(([, value]) => value !== ''))
        return request('/admin/content/articles?' + values)
      }, filters)
    } else if (scope === 'retry') items = retryItems.value.map(item => ({ contentId: item.contentId, title: item.title }))
    else items = articles.value.items.filter(item => selectedIds.value.includes(item.contentId))
    if (!items.some(item => !item.articleAudioUrl)) { audioSuccess.value = '当前范围内没有需要生成音频的短文。'; return }
    audioTarget.value = {
      batch: true, items,
      title: scope === 'all' ? '当前筛选结果（跨全部分页）' : scope === 'retry' ? '失败及未处理项' : '本页勾选的短文',
      count: items.filter(item => !item.articleAudioUrl).length,
      skipped: items.filter(item => item.articleAudioUrl).length
    }
  } catch (e) { error.value = e.message || '批量范围读取失败' }
  finally { batchPreparing.value = false }
}
async function generateBatchAudio() {
  if (audioBusy.value || !audioTarget.value?.batch) return
  const token = nlsToken.value.trim()
  if (token.length < 16 || token.length > 4096) { audioError.value = '请输入有效的临时 NLS Token'; return }
  const items = audioTarget.value.items
  batchRunning.value = true; batchStopRequested.value = false
  nlsToken.value = ''; audioTarget.value = null; audioError.value = ''
  try {
    const result = await runArticleAudioBatch({
      items, nlsToken: token,
      shouldStop: () => batchStopRequested.value,
      generate: (id, temporaryToken) => request('/admin/content/articles/' + encodeURIComponent(id) + '/speech', {
        method: 'POST', body: JSON.stringify({ nlsToken: temporaryToken }), silentError: true
      }),
      onProgress: state => { batchProgress.value = state },
      onSaved: updateSavedAudio
    })
    audioSuccess.value = result.stopped ? '批量处理已停止，已完成的音频已保存。可重新输入 Token 继续失败及未处理项。' : '批量处理结束，成功项已上传 OSS 并保存。'
  } catch (e) { error.value = e.message || '批量生成失败'; showError(e) }
  finally { batchRunning.value = false; nlsToken.value = '' }
}
function beforeUnload(event) {
  if (!audioBusy.value) return
  event.preventDefault(); event.returnValue = ''
}
onBeforeRouteLeave(() => {
  if (!audioBusy.value) return true
  showError(new Error('音频任务正在处理，请先点击“停止后续生成”，等待当前音频保存后再离开。'))
  return false
})

const levels = computed(() => {
  const counts = Object.fromEntries((coverage.value.articleDifficulties || []).map(item => [item.name, item.itemCount]))
  return [
    { code: 'intro', name: '入门', count: counts['入门'] || 0 },
    { code: 'advanced', name: '进阶', count: counts['进阶'] || 0 }
  ]
})
const publishedTotal = computed(() => levels.value.reduce((sum, item) => sum + item.count, 0))
const insufficientCount = computed(() => levels.value.filter(item => item.count < 101).length)
const detailUrl = computed(() => {
  const value = detail.value?.originUrl || detail.value?.sourceUrl
  if (!value) return ''
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : '' }
  catch { return '' }
})

function params() {
  const value = new URLSearchParams({ page: String(page.value), pageSize: String(pageSize.value) })
  if (difficulty.value) value.set('difficulty', difficulty.value)
  if (bookId.value) value.set('bookId', bookId.value)
  if (keyword.value) value.set('keyword', keyword.value)
  return value
}
async function load() {
  if (audioBusy.value) return
  selectedIds.value = []
  loading.value = true; error.value = ''
  try {
    const [coverageData, articleData, bookData] = await Promise.all([
      request('/admin/content/coverage'), request(`/admin/content/articles?${params()}`), request('/admin/vocabulary-books')
    ])
    coverage.value = coverageData; articles.value = articleData; books.value = bookData || []; page.value = articleData.page
  } catch (e) { error.value = e.message || '英语短文加载失败' }
  finally { loading.value = false }
}
async function loadArticles() {
  selectedIds.value = []
  listLoading.value = true; error.value = ''
  try { articles.value = await request(`/admin/content/articles?${params()}`); page.value = articles.value.page }
  catch (e) { error.value = e.message || '英语短文加载失败' }
  finally { listLoading.value = false }
}
function chooseLevel(code = '') { difficulty.value = code; page.value = 1; loadArticles() }
function chooseBook(code = '') { bookId.value = code; page.value = 1; loadArticles() }
function search() { keyword.value = keywordInput.value.trim(); page.value = 1; loadArticles() }
function clearSearch() { keywordInput.value = ''; keyword.value = ''; page.value = 1; loadArticles() }
function turn(target) { if (target < 1 || target > articles.value.totalPages || target === page.value) return; page.value = target; loadArticles() }
async function openDetail(id) {
  detailOpen.value = true; detailLoading.value = true; detailError.value = ''; detail.value = null
  try { detail.value = await request(`/admin/content/articles/${encodeURIComponent(id)}`) }
  catch (e) { detailError.value = e.message || '短文详情加载失败' }
  finally { detailLoading.value = false }
}
function stopAudio() { if (currentAudio) { currentAudio.pause(); currentAudio = null } }
function closeDetail() { stopAudio(); detailOpen.value = false; detail.value = null; detailError.value = '' }
async function playAudio(item) {
  if (!item?.articleAudioUrl) return
  try { stopAudio(); currentAudio = new Audio(item.articleAudioUrl); await currentAudio.play() }
  catch { const e=new Error('音频播放失败，请检查浏览器权限或 OSS 媒体配置'); error.value=e.message; showError(e) }
}
function openAudioGeneration(item) {
  if (audioBusy.value) return
  audioTarget.value = item; nlsToken.value = ''; audioError.value = ''; audioSuccess.value = ''
}
function closeAudioGeneration() {
  if (generatingArticleId.value) return
  audioTarget.value = null; nlsToken.value = ''; audioError.value = ''
}
async function generateAudio() {
  const item = audioTarget.value
  if (!item?.contentId || generatingArticleId.value) return
  const token = nlsToken.value.trim()
  if (token.length < 16 || token.length > 4096) { audioError.value = '请输入有效的临时 NLS Token'; return }
  generatingArticleId.value = item.contentId; error.value = ''
  audioError.value = ''
  try {
    const result = await request(`/admin/content/articles/${encodeURIComponent(item.contentId)}/speech`, {
      method: 'POST', body: JSON.stringify({ nlsToken: token })
    })
    item.articleAudioUrl = result.audioUrl
    item.articleAudioAssetId = result.assetId || item.articleAudioAssetId
    if (detail.value?.contentId === item.contentId) detail.value.articleAudioUrl = result.audioUrl
    audioSuccess.value = result.cached ? '已有音频，已复用，可点击播放。' : '音频已上传 OSS，地址与短文关联已保存到数据库，可点击播放。'
    audioTarget.value = null
  } catch (e) { audioError.value = e.message || '音频生成失败' }
  finally { generatingArticleId.value = ''; nlsToken.value = '' }
}
function label(value) { return ({ intro: '入门', advanced: '进阶' })[value] || value || '未分级' }
function date(value) { return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)) : '—' }
watch(pageSize, () => { page.value = 1; if (!loading.value) loadArticles() })
onMounted(() => { load(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { batchStopRequested.value = true; stopAudio(); nlsToken.value = ''; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>英语短文</h1><p>按难度管理已发布短文，查看正文、来源与许可信息。</p></div><button class="refresh" :disabled="audioBusy" @click="load">刷新数据</button></div>
    <div class="tts-mode temporary">支持单篇或批量生成：批量只需输入一次临时 NLS Token，逐篇上传 OSS 并保存。已有音频自动跳过。</div>
    <div v-if="audioSuccess" class="tts-mode temporary" role="status">{{ audioSuccess }}</div>
    <div v-if="batchProgress" class="panel batch-progress" role="status" aria-live="polite">
      <h3>{{ batchRunning ? '正在批量生成音频' : batchProgress.stopped ? '批量处理已停止' : '批量处理结果' }}</h3>
      <progress :value="batchProgress.done" :max="batchProgress.total || 1"></progress>
      <p>已处理 {{ batchProgress.done }} / {{ batchProgress.total }} 篇 · 已保存 {{ batchCounts.succeeded }} · 跳过 {{ batchCounts.skipped }} · 失败 {{ batchCounts.failed }} · 未处理 {{ batchCounts.pending }}</p>
      <p v-if="batchRunning">请保持当前页面打开；停止会等待当前请求完成，不会撤销已保存音频。</p>
      <button v-if="batchRunning" class="refresh" :disabled="batchStopRequested" @click="batchStopRequested=true">{{ batchStopRequested ? '等待当前音频保存…' : '停止后续生成' }}</button>
      <button v-else-if="retryItems.length" class="refresh" :disabled="audioBusy" @click="openBatchGeneration('retry')">重新输入 Token，继续失败及未处理项（{{ retryItems.length }}）</button>
      <details><summary>查看逐篇结果及失败原因</summary><ul class="batch-results"><li v-for="item in batchProgress.items" :key="item.contentId"><strong>{{ item.title }}</strong> — {{ batchStatus(item.status) }}<span v-if="item.error">：{{ item.error }}（{{ item.code || 'NETWORK_ERROR' }}）</span></li></ul></details>
    </div>
    <div v-if="loading" class="panel state">正在读取英语短文…</div>
    <template v-else>
      <div class="metrics">
        <div class="panel metric"><span>已发布短文</span><strong>{{ publishedTotal }}</strong></div>
        <div class="panel metric"><span>难度级别</span><strong>{{ levels.length }}</strong></div>
        <div class="panel metric"><span>未达 101 篇</span><strong :class="{ danger: insufficientCount }">{{ insufficientCount }}</strong></div>
      </div>
      <div class="panel level-picker">
        <label>词书分类<select :disabled="audioBusy" :value="bookId" @change="chooseBook($event.target.value)"><option value="">全部词书</option><option v-for="book in books" :key="book.bookId" :value="book.bookId">{{ book.bookName }}</option></select></label>
        <label>难度筛选<select :disabled="audioBusy" :value="difficulty" @change="chooseLevel($event.target.value)"><option value="">全部难度</option><option v-for="item in levels" :key="item.code" :value="item.code">{{ item.name }}</option></select></label>
        <div class="level-summary"><span v-for="item in levels" :key="item.code"><b>{{ item.name }}</b> {{ item.count }} / 101 篇 <em :class="item.count>=101?'ok':'bad'">{{ item.count>=101?'已达标':'待补充' }}</em></span></div>
      </div>
      <div class="panel article-panel">
        <div class="panel-head"><div><h2>短文列表</h2><p>{{ bookId ? `当前词书：${books.find(item=>item.bookId===bookId)?.bookName || '未知'}` : difficulty ? `当前难度：${label(difficulty)}` : '全部已发布英语短文' }} · 共 {{ articles.total }} 篇</p></div><button v-if="difficulty || bookId" :disabled="audioBusy" class="plain" @click="difficulty='';bookId='';page=1;loadArticles()">清除筛选</button></div>
        <div class="toolbar"><form @submit.prevent="!audioBusy && search()"><input :disabled="audioBusy" v-model="keywordInput" maxlength="80" placeholder="搜索标题、摘要或正文"/><button :disabled="audioBusy">搜索</button><button v-if="keyword" :disabled="audioBusy" type="button" class="plain" @click="clearSearch">清除</button></form><label>每页 <select :disabled="audioBusy" v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 篇</label></div>
        <div class="batch-toolbar">
          <button class="refresh" :disabled="audioBusy || listLoading || !selectedIds.length" @click="openBatchGeneration('selected')">批量生成勾选项（{{ selectedIds.length }}）</button>
          <button class="refresh" :disabled="audioBusy || listLoading || !articles.total" @click="openBatchGeneration('all')">{{ batchPreparing ? '正在读取范围…' : '批量生成当前筛选全部' }}</button>
          <span>勾选仅针对本页；“筛选全部”包含所有分页，开始前会确认篇数。</span>
        </div>
        <div class="table-wrap"><table><thead><tr><th><input type="checkbox" aria-label="全选本页未生成音频的短文" :checked="allPageSelected" :disabled="audioBusy || listLoading || !selectableItems.length" @change="selectPage($event.target.checked)" /></th><th>标题</th><th>适用词书</th><th>难度</th><th>阅读时长</th><th>来源</th><th>来源日期</th><th>操作</th></tr></thead><tbody><tr v-for="item in articles.items" :key="item.contentId"><td><input v-model="selectedIds" type="checkbox" :value="item.contentId" :aria-label="'选择 ' + item.title" :disabled="audioBusy || listLoading || !!item.articleAudioUrl" /></td><td><strong>{{ item.title }}</strong><small>{{ item.summary || '暂无摘要' }}</small></td><td>{{ item.audienceBookNames || '未分类' }}</td><td><span class="difficulty">{{ label(item.difficulty) }}</span></td><td>{{ Math.max(1,Math.ceil((item.estimatedSeconds||0)/60)) }} 分钟</td><td>{{ item.sourceName }}</td><td>{{ date(item.originPublishedAt || item.publishedAt) }}</td><td><div class="article-actions"><button class="link" @click="openDetail(item.contentId)">查看详情 ›</button><button v-if="item.articleAudioUrl" class="audio-button" @click="playAudio(item)">▶ 播放</button><button v-else class="generate-button" :disabled="audioBusy" @click="openAudioGeneration(item)">{{ generatingArticleId===item.contentId ? '生成中…' : '生成音频' }}</button></div></td></tr><tr v-if="!listLoading&&!articles.items.length"><td colspan="8" class="empty">没有符合条件的英语短文</td></tr></tbody></table><div v-if="listLoading" class="loading">正在加载…</div></div>
        <div class="pagination"><span>{{ articles.total ? `${(articles.page-1)*articles.pageSize+1}–${Math.min(articles.total,articles.page*articles.pageSize)} / ${articles.total}` : '0 篇' }}</span><button :disabled="audioBusy || page<=1" @click="turn(page-1)">‹</button><span>第 {{ page }} / {{ articles.totalPages || 1 }} 页</span><button :disabled="audioBusy || page>=articles.totalPages" @click="turn(page+1)">›</button></div>
      </div>
    </template>
  </section>

  <div v-if="audioTarget" class="modal-mask" @click.self="closeAudioGeneration">
    <form class="audio-modal" role="dialog" aria-modal="true" aria-labelledby="audio-modal-title" @submit.prevent="audioTarget.batch ? generateBatchAudio() : generateAudio()">
      <h2 id="audio-modal-title">{{ audioTarget.batch ? '批量生成并保存音频' : '临时 Token 生成并保存音频' }}</h2>
      <p>{{ audioTarget.title }}</p>
      <p v-if="audioTarget.batch">待生成 <strong>{{ audioTarget.count }}</strong> 篇，已有音频跳过 {{ audioTarget.skipped }} 篇。只需输入一次 Token，请保持页面打开，批量结束后自动清除 Token。</p>
      <p>使用当前配置的 AppKey、音色和采样率。仅本次生成过程使用 Token，不保存 Token，也不调用 AccessKey 获取 Token。会产生 NLS 合成及 OSS 存储用量。</p>
      <label for="article-nls-token">NLS 临时 Token</label>
      <input id="article-nls-token" v-model="nlsToken" type="password" autocomplete="off" minlength="16" maxlength="4096" required :disabled="!!generatingArticleId" placeholder="粘贴阿里云控制台获取的临时 Token" />
      <p>Token 过期需重新获取；OSS 上传仍需服务端独立配置 OSS 凭据。</p>
      <p v-if="audioError" class="notice" role="alert">{{ audioError }}</p>
      <div class="audio-modal-actions"><button type="button" class="refresh" :disabled="!!generatingArticleId" @click="closeAudioGeneration">取消</button><button class="generate-button" :disabled="!!generatingArticleId">{{ generatingArticleId ? '正在合成、上传并保存…' : audioTarget.batch ? '开始批量生成并保存' : '生成并保存到 OSS' }}</button></div>
    </form>
  </div>
  <div v-if="detailOpen" class="modal-mask" @click.self="closeDetail"><article class="detail-modal"><header><div><span>英语短文详情</span><h2>{{ detail?.title || '正在加载…' }}</h2></div><button @click="closeDetail">×</button></header><div v-if="detailLoading" class="detail-state">正在读取详情…</div><div v-else-if="detailError" class="detail-state">详情未加载，请关闭后重试</div><template v-else-if="detail"><div class="detail-meta"><span>{{ label(detail.difficulty) }}</span><span>预计 {{ Math.max(1,Math.ceil((detail.estimatedSeconds||0)/60)) }} 分钟</span><span>版本 {{ detail.versionNo }}</span><span>{{ detail.reviewStatus==='approved'?'已审核':detail.reviewStatus }}</span></div><section v-if="detail.summary"><h3>摘要</h3><p>{{ detail.summary }}</p></section><section><h3>英文正文</h3><div class="body">{{ detail.body || '暂无正文' }}</div></section><section class="source-box"><h3>来源与许可</h3><dl><div><dt>内容来源</dt><dd>{{ detail.sourceName }}（{{ detail.sourceType }}）</dd></div><div><dt>原作者</dt><dd>{{ detail.originAuthor || '—' }}</dd></div><div><dt>原文发布时间</dt><dd>{{ date(detail.originPublishedAt) }}</dd></div><div><dt>站内发布时间</dt><dd>{{ date(detail.publishedAt) }}</dd></div><div><dt>许可快照</dt><dd>{{ detail.licenseSnapshot }}</dd></div></dl><a v-if="detailUrl" :href="detailUrl" target="_blank" rel="noopener noreferrer">打开原始链接 ↗</a></section></template></article></div>
</template>

<style scoped>
.batch-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:14px 0}.batch-toolbar span{font-size:12px;color:#71809a}.batch-progress{margin-top:18px;padding:20px}.batch-progress p{color:#65738d;font-size:13px}.batch-progress progress{width:100%;height:14px;accent-color:#0d6ff5}.batch-progress details{margin-top:14px}.batch-results{max-height:220px;overflow:auto;line-height:1.8;padding-left:22px;font-size:13px}.batch-results span{color:#b83c48}button:disabled{cursor:not-allowed;opacity:.6}

.audio-modal{width:min(620px,90vw);max-height:90vh;overflow:auto;padding:28px;border-radius:16px;background:white}.audio-modal p{color:#65738d;line-height:1.7}.audio-modal label{display:block;margin:16px 0 8px}.audio-modal input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #c9daf3;border-radius:8px}.audio-modal-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:22px}.audio-modal-actions .generate-button{height:40px;padding:0 18px}
.refresh{height:40px;padding:0 18px;border:1px solid #c9daf3;border-radius:9px;color:#126ff4;background:#fff}.tts-mode{margin-top:18px;padding:12px 15px;border-radius:9px;font-size:13px}.tts-mode.temporary{border:1px solid #bfe8d7;color:#087951;background:#e8f8f2}.tts-mode.access-key{border:1px solid #d9e2ef;color:#65738d;background:#f7f9fc}.notice{margin-top:18px;padding:13px 16px;border:1px solid #efc7cb;border-radius:10px;color:#b83c48;background:#fff4f5}.state{margin-top:24px;padding:60px;text-align:center;color:#71809a}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:24px}.metric{padding:22px}.metric span{display:block;color:#71809a;font-size:13px}.metric strong{display:block;margin-top:10px;font-size:30px}.danger{color:#d8424f}.level-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:18px;margin-top:18px}.level-card{padding:20px;border:1px solid #dde6f1;text-align:left;cursor:pointer}.level-card.active{border-color:#3d8cff;background:linear-gradient(145deg,#fff,#eef5ff)}.level-card span,.level-card strong{display:block}.level-card span{color:#667590}.level-card strong{margin:10px 0;font-size:25px}.level-card small{color:#7a879c;font-size:12px}.level-card em,.difficulty{display:inline-block;padding:4px 9px;border-radius:99px;font-size:12px;font-style:normal}.ok{color:#15845f;background:#e8f8f1}.bad{color:#c43a47;background:#fff0f1}.difficulty{color:#176fdc;background:#eaf3ff}.article-panel{margin-top:18px;padding:22px}.panel-head,.toolbar,.pagination{display:flex;align-items:center;justify-content:space-between}.panel-head h2{margin:0 0 5px}.panel-head p{margin:0;color:#7a869d;font-size:12px}.plain{border:1px solid #d7dfeb!important;color:#62718c!important;background:#fff!important}.toolbar{margin:18px 0 13px}.toolbar form{display:flex;gap:8px}.toolbar input{width:340px;height:38px;padding:0 11px;border:1px solid #d7dfeb;border-radius:8px}.toolbar button{height:38px;padding:0 16px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}.toolbar label{color:#6c7890;font-size:12px}.toolbar select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}.table-wrap{position:relative;min-height:220px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}table{width:100%;border-collapse:collapse}th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left;font-size:12px}th{color:#75819a;background:#f8faff}td{color:#53617a}td strong,td small{display:block}td strong{color:#11204a;font-size:14px}td small{max-width:390px;margin-top:5px;overflow:hidden;color:#7c889d;text-overflow:ellipsis;white-space:nowrap}.link{padding:4px;border:0;color:#0d6ff5;background:transparent;white-space:nowrap}.empty{height:170px;text-align:center;color:#8994a7}.loading{position:absolute;inset:42px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}.pagination button:disabled{color:#bbc3cf}.detail-modal{width:min(820px,88vw);max-height:88vh;overflow:auto;border-radius:16px;background:#fff;box-shadow:0 25px 70px rgba(19,36,70,.24)}.detail-modal header{position:sticky;top:0;z-index:2;display:flex;justify-content:space-between;padding:24px 28px;border-bottom:1px solid #e8edf4;background:#fff}.detail-modal header span{color:#72809a;font-size:12px}.detail-modal header h2{margin:6px 0 0}.detail-modal header button{border:0;background:transparent;color:#6f7d95;font-size:28px}.detail-state{padding:80px;text-align:center;color:#73819a}.detail-state.error{color:#c53f4c}.detail-meta,.detail-modal>section{margin:20px 28px}.detail-meta{display:flex;gap:8px}.detail-meta span{padding:5px 9px;border-radius:6px;color:#566681;background:#f1f4f8;font-size:12px}.detail-modal section h3{margin:0 0 10px;font-size:15px}.detail-modal section p,.body{color:#3e4d69;line-height:1.8}.body{white-space:pre-wrap;user-select:text}.source-box{padding:18px;border-radius:10px;background:#f7f9fc}.source-box dl{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.source-box dt{color:#8490a4;font-size:11px}.source-box dd{margin:5px 0 0;overflow-wrap:anywhere;color:#42516d;font-size:13px}.source-box a{display:inline-block;margin-top:14px;color:#0d6ff5;text-decoration:none}@media(max-width:1100px){.metrics{grid-template-columns:1fr 1fr}.level-grid{grid-template-columns:1fr}.source-box dl{grid-template-columns:1fr}}
.level-picker{display:flex;align-items:center;gap:26px;margin-top:18px;padding:14px 18px}.level-picker label{display:flex;align-items:center;gap:10px;color:#667590;font-size:12px}.level-picker select{width:170px;height:38px;padding:0 10px;border:1px solid #d7dfeb;border-radius:8px;background:#fff}.level-summary{display:flex;flex-wrap:wrap;gap:16px;color:#667590;font-size:12px}.level-summary span{display:flex;align-items:center;gap:6px}.level-summary em{display:inline-block;padding:4px 9px;border-radius:99px;font-size:12px;font-style:normal}@media(max-width:1100px){.level-picker{align-items:flex-start;flex-direction:column}}
 .article-actions{display:flex;align-items:center;gap:8px}.audio-button,.generate-button{height:29px;padding:0 9px;border:1px solid #b9d3fb;border-radius:7px;color:#0d6ff5;background:#fff;white-space:nowrap}.generate-button{border-color:#0d6ff5;color:#fff;background:#0d6ff5}.generate-button:disabled{opacity:.62}
</style>
