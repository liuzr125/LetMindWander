<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'

const loading = ref(true), listLoading = ref(false), error = ref('')
const coverage = ref({ articleDifficulties: [] })
const articles = ref({ total: 0, page: 1, pageSize: 20, totalPages: 0, items: [] })
const page = ref(1), pageSize = ref(20), difficulty = ref(''), keywordInput = ref(''), keyword = ref('')
const detailOpen = ref(false), detailLoading = ref(false), detailError = ref(''), detail = ref(null)
const generatingArticleId = ref('')
let currentAudio = null

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
  if (keyword.value) value.set('keyword', keyword.value)
  return value
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const [coverageData, articleData] = await Promise.all([
      request('/admin/content/coverage'), request(`/admin/content/articles?${params()}`)
    ])
    coverage.value = coverageData; articles.value = articleData; page.value = articleData.page
  } catch (e) { error.value = e.message || '英语短文加载失败' }
  finally { loading.value = false }
}
async function loadArticles() {
  listLoading.value = true; error.value = ''
  try { articles.value = await request(`/admin/content/articles?${params()}`); page.value = articles.value.page }
  catch (e) { error.value = e.message || '英语短文加载失败' }
  finally { listLoading.value = false }
}
function chooseLevel(code = '') { difficulty.value = code; page.value = 1; loadArticles() }
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
  catch { error.value = '音频播放失败，请检查浏览器权限或 OSS 媒体配置' }
}
async function generateAudio(item) {
  if (!item?.contentId || generatingArticleId.value) return
  generatingArticleId.value = item.contentId; error.value = ''
  try {
    const result = await request(`/admin/content/articles/${encodeURIComponent(item.contentId)}/speech`, { method: 'POST' })
    item.articleAudioUrl = result.audioUrl
    item.articleAudioAssetId = result.assetId || item.articleAudioAssetId
    if (detail.value?.contentId === item.contentId) detail.value.articleAudioUrl = result.audioUrl
  } catch (e) { error.value = e.message || '音频生成失败' }
  finally { generatingArticleId.value = '' }
}
function label(value) { return ({ intro: '入门', advanced: '进阶' })[value] || value || '未分级' }
function date(value) { return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)) : '—' }
watch(pageSize, () => { page.value = 1; if (!loading.value) loadArticles() })
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>英语短文</h1><p>按难度管理已发布短文，查看正文、来源与许可信息。</p></div><button class="refresh" @click="load">刷新数据</button></div>
    <div v-if="error" class="notice">{{ error }}</div>
    <div v-if="loading" class="panel state">正在读取英语短文…</div>
    <template v-else>
      <div class="metrics">
        <div class="panel metric"><span>已发布短文</span><strong>{{ publishedTotal }}</strong></div>
        <div class="panel metric"><span>难度级别</span><strong>{{ levels.length }}</strong></div>
        <div class="panel metric"><span>未达 101 篇</span><strong :class="{ danger: insufficientCount }">{{ insufficientCount }}</strong></div>
      </div>
      <div class="panel level-picker">
        <label>难度筛选<select :value="difficulty" @change="chooseLevel($event.target.value)"><option value="">全部难度</option><option v-for="item in levels" :key="item.code" :value="item.code">{{ item.name }}</option></select></label>
        <div class="level-summary"><span v-for="item in levels" :key="item.code"><b>{{ item.name }}</b> {{ item.count }} / 101 篇 <em :class="item.count>=101?'ok':'bad'">{{ item.count>=101?'已达标':'待补充' }}</em></span></div>
      </div>
      <div class="panel article-panel">
        <div class="panel-head"><div><h2>短文列表</h2><p>{{ difficulty ? `当前难度：${label(difficulty)}` : '全部已发布英语短文' }} · 共 {{ articles.total }} 篇</p></div><button v-if="difficulty" class="plain" @click="chooseLevel('')">清除难度</button></div>
        <div class="toolbar"><form @submit.prevent="search"><input v-model="keywordInput" maxlength="80" placeholder="搜索标题、摘要或正文"/><button>搜索</button><button v-if="keyword" type="button" class="plain" @click="clearSearch">清除</button></form><label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 篇</label></div>
        <div class="table-wrap"><table><thead><tr><th>标题</th><th>难度</th><th>阅读时长</th><th>来源</th><th>来源日期</th><th>操作</th></tr></thead><tbody><tr v-for="item in articles.items" :key="item.contentId"><td><strong>{{ item.title }}</strong><small>{{ item.summary || '暂无摘要' }}</small></td><td><span class="difficulty">{{ label(item.difficulty) }}</span></td><td>{{ Math.max(1,Math.ceil((item.estimatedSeconds||0)/60)) }} 分钟</td><td>{{ item.sourceName }}</td><td>{{ date(item.originPublishedAt || item.publishedAt) }}</td><td><div class="article-actions"><button class="link" @click="openDetail(item.contentId)">查看详情 ›</button><button v-if="item.articleAudioUrl" class="audio-button" @click="playAudio(item)">▶ 播放</button><button v-else class="generate-button" :disabled="generatingArticleId===item.contentId" @click="generateAudio(item)">{{ generatingArticleId===item.contentId ? '生成中…' : '生成音频' }}</button></div></td></tr><tr v-if="!listLoading&&!articles.items.length"><td colspan="6" class="empty">没有符合条件的英语短文</td></tr></tbody></table><div v-if="listLoading" class="loading">正在加载…</div></div>
        <div class="pagination"><span>{{ articles.total ? `${(articles.page-1)*articles.pageSize+1}–${Math.min(articles.total,articles.page*articles.pageSize)} / ${articles.total}` : '0 篇' }}</span><button :disabled="page<=1" @click="turn(page-1)">‹</button><span>第 {{ page }} / {{ articles.totalPages || 1 }} 页</span><button :disabled="page>=articles.totalPages" @click="turn(page+1)">›</button></div>
      </div>
    </template>
  </section>

  <div v-if="detailOpen" class="modal-mask" @click.self="closeDetail"><article class="detail-modal"><header><div><span>英语短文详情</span><h2>{{ detail?.title || '正在加载…' }}</h2></div><button @click="closeDetail">×</button></header><div v-if="detailLoading" class="detail-state">正在读取详情…</div><div v-else-if="detailError" class="detail-state error">{{ detailError }}</div><template v-else-if="detail"><div class="detail-meta"><span>{{ label(detail.difficulty) }}</span><span>预计 {{ Math.max(1,Math.ceil((detail.estimatedSeconds||0)/60)) }} 分钟</span><span>版本 {{ detail.versionNo }}</span><span>{{ detail.reviewStatus==='approved'?'已审核':detail.reviewStatus }}</span></div><section v-if="detail.summary"><h3>摘要</h3><p>{{ detail.summary }}</p></section><section><h3>英文正文</h3><div class="body">{{ detail.body || '暂无正文' }}</div></section><section class="source-box"><h3>来源与许可</h3><dl><div><dt>内容来源</dt><dd>{{ detail.sourceName }}（{{ detail.sourceType }}）</dd></div><div><dt>原作者</dt><dd>{{ detail.originAuthor || '—' }}</dd></div><div><dt>原文发布时间</dt><dd>{{ date(detail.originPublishedAt) }}</dd></div><div><dt>站内发布时间</dt><dd>{{ date(detail.publishedAt) }}</dd></div><div><dt>许可快照</dt><dd>{{ detail.licenseSnapshot }}</dd></div></dl><a v-if="detailUrl" :href="detailUrl" target="_blank" rel="noopener noreferrer">打开原始链接 ↗</a></section></template></article></div>
</template>

<style scoped>
.refresh{height:40px;padding:0 18px;border:1px solid #c9daf3;border-radius:9px;color:#126ff4;background:#fff}.notice{margin-top:18px;padding:13px 16px;border:1px solid #efc7cb;border-radius:10px;color:#b83c48;background:#fff4f5}.state{margin-top:24px;padding:60px;text-align:center;color:#71809a}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:24px}.metric{padding:22px}.metric span{display:block;color:#71809a;font-size:13px}.metric strong{display:block;margin-top:10px;font-size:30px}.danger{color:#d8424f}.level-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:18px;margin-top:18px}.level-card{padding:20px;border:1px solid #dde6f1;text-align:left;cursor:pointer}.level-card.active{border-color:#3d8cff;background:linear-gradient(145deg,#fff,#eef5ff)}.level-card span,.level-card strong{display:block}.level-card span{color:#667590}.level-card strong{margin:10px 0;font-size:25px}.level-card small{color:#7a879c;font-size:12px}.level-card em,.difficulty{display:inline-block;padding:4px 9px;border-radius:99px;font-size:12px;font-style:normal}.ok{color:#15845f;background:#e8f8f1}.bad{color:#c43a47;background:#fff0f1}.difficulty{color:#176fdc;background:#eaf3ff}.article-panel{margin-top:18px;padding:22px}.panel-head,.toolbar,.pagination{display:flex;align-items:center;justify-content:space-between}.panel-head h2{margin:0 0 5px}.panel-head p{margin:0;color:#7a869d;font-size:12px}.plain{border:1px solid #d7dfeb!important;color:#62718c!important;background:#fff!important}.toolbar{margin:18px 0 13px}.toolbar form{display:flex;gap:8px}.toolbar input{width:340px;height:38px;padding:0 11px;border:1px solid #d7dfeb;border-radius:8px}.toolbar button{height:38px;padding:0 16px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}.toolbar label{color:#6c7890;font-size:12px}.toolbar select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}.table-wrap{position:relative;min-height:220px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}table{width:100%;border-collapse:collapse}th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left;font-size:12px}th{color:#75819a;background:#f8faff}td{color:#53617a}td strong,td small{display:block}td strong{color:#11204a;font-size:14px}td small{max-width:390px;margin-top:5px;overflow:hidden;color:#7c889d;text-overflow:ellipsis;white-space:nowrap}.link{padding:4px;border:0;color:#0d6ff5;background:transparent;white-space:nowrap}.empty{height:170px;text-align:center;color:#8994a7}.loading{position:absolute;inset:42px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}.pagination button:disabled{color:#bbc3cf}.detail-modal{width:min(820px,88vw);max-height:88vh;overflow:auto;border-radius:16px;background:#fff;box-shadow:0 25px 70px rgba(19,36,70,.24)}.detail-modal header{position:sticky;top:0;z-index:2;display:flex;justify-content:space-between;padding:24px 28px;border-bottom:1px solid #e8edf4;background:#fff}.detail-modal header span{color:#72809a;font-size:12px}.detail-modal header h2{margin:6px 0 0}.detail-modal header button{border:0;background:transparent;color:#6f7d95;font-size:28px}.detail-state{padding:80px;text-align:center;color:#73819a}.detail-state.error{color:#c53f4c}.detail-meta,.detail-modal>section{margin:20px 28px}.detail-meta{display:flex;gap:8px}.detail-meta span{padding:5px 9px;border-radius:6px;color:#566681;background:#f1f4f8;font-size:12px}.detail-modal section h3{margin:0 0 10px;font-size:15px}.detail-modal section p,.body{color:#3e4d69;line-height:1.8}.body{white-space:pre-wrap;user-select:text}.source-box{padding:18px;border-radius:10px;background:#f7f9fc}.source-box dl{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.source-box dt{color:#8490a4;font-size:11px}.source-box dd{margin:5px 0 0;overflow-wrap:anywhere;color:#42516d;font-size:13px}.source-box a{display:inline-block;margin-top:14px;color:#0d6ff5;text-decoration:none}@media(max-width:1100px){.metrics{grid-template-columns:1fr 1fr}.level-grid{grid-template-columns:1fr}.source-box dl{grid-template-columns:1fr}}
.level-picker{display:flex;align-items:center;gap:26px;margin-top:18px;padding:14px 18px}.level-picker label{display:flex;align-items:center;gap:10px;color:#667590;font-size:12px}.level-picker select{width:170px;height:38px;padding:0 10px;border:1px solid #d7dfeb;border-radius:8px;background:#fff}.level-summary{display:flex;flex-wrap:wrap;gap:16px;color:#667590;font-size:12px}.level-summary span{display:flex;align-items:center;gap:6px}.level-summary em{display:inline-block;padding:4px 9px;border-radius:99px;font-size:12px;font-style:normal}@media(max-width:1100px){.level-picker{align-items:flex-start;flex-direction:column}}
 .article-actions{display:flex;align-items:center;gap:8px}.audio-button,.generate-button{height:29px;padding:0 9px;border:1px solid #b9d3fb;border-radius:7px;color:#0d6ff5;background:#fff;white-space:nowrap}.generate-button{border-color:#0d6ff5;color:#fff;background:#0d6ff5}.generate-button:disabled{opacity:.62}
</style>
