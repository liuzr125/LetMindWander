<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'

const loading = ref(true), error = ref('')
const coverage = ref({ publishedTotal: 0, sourceCount: 0, technicalTopics: [], wordStages: [], articleDifficulties: [] })
const technical = ref({ total: 0, page: 1, pageSize: 20, totalPages: 0, items: [] })
const techLoading = ref(false), techError = ref(''), page = ref(1), pageSize = ref(20)
const topic = ref(''), keywordInput = ref(''), keyword = ref('')
const detailOpen = ref(false), detailLoading = ref(false), detailError = ref(''), detail = ref(null)
const detailUrl = computed(() => {
  const value = detail.value?.originUrl || detail.value?.sourceUrl
  if (!value) return ''
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : '' }
  catch { return '' }
})

const insufficient = computed(() => [
  ...coverage.value.technicalTopics.map(item => ({ ...item, required: 25 })),
  ...(coverage.value.articleDifficulties || []).map(item => ({ ...item, required: 101 }))
].filter(item => item.itemCount < item.required))

async function load() {
  loading.value = true; error.value = ''
  try {
    const [coverageData, technicalData] = await Promise.all([request('/admin/content/coverage'), technicalRequest()])
    coverage.value = coverageData; technical.value = technicalData; page.value = technicalData.page
  } catch (e) { error.value = e.message || '内容数据加载失败' }
  finally { loading.value = false }
}
function technicalRequest() {
  const params = new URLSearchParams({ page: String(page.value), pageSize: String(pageSize.value) })
  if (topic.value) params.set('topic', topic.value)
  if (keyword.value) params.set('keyword', keyword.value)
  return request(`/admin/content/technical?${params}`)
}
async function loadTechnical() {
  techLoading.value = true; techError.value = ''
  try { technical.value = await technicalRequest(); page.value = technical.value.page }
  catch (e) { techError.value = e.message || '技术知识加载失败' }
  finally { techLoading.value = false }
}
function chooseTopic(name = '') { topic.value = name; page.value = 1; loadTechnical() }
function search() { keyword.value = keywordInput.value.trim(); page.value = 1; loadTechnical() }
function clearSearch() { keywordInput.value = ''; keyword.value = ''; page.value = 1; loadTechnical() }
function turn(target) { if (target < 1 || target > technical.value.totalPages || target === page.value) return; page.value = target; loadTechnical() }
async function openDetail(id) {
  detailOpen.value = true; detailLoading.value = true; detailError.value = ''; detail.value = null
  try { detail.value = await request(`/admin/content/technical/${encodeURIComponent(id)}`) }
  catch (e) { detailError.value = e.message || '详情加载失败' }
  finally { detailLoading.value = false }
}
function closeDetail() { detailOpen.value = false; detail.value = null; detailError.value = '' }
function date(value) { return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)) : '—' }
function difficulty(value) { return ({ intro: '入门', advanced: '进阶' })[value] || value || '未分级' }
watch(pageSize, () => { page.value = 1; if (!loading.value) loadTechnical() })
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>内容与来源</h1><p>核对技术主题与英语短文的初始化覆盖率，发布内容仍需保留来源与许可。</p></div><button class="refresh" @click="load">刷新数据</button></div>
    <div v-if="loading" class="panel state">正在读取内容…</div>
    <div v-else-if="error" class="panel state error"><strong>{{ error }}</strong><button class="retry" @click="load">重新加载</button></div>
    <template v-else>
      <div class="metrics"><div class="panel metric"><span>已发布内容</span><strong>{{ coverage.publishedTotal }}</strong></div><div class="panel metric"><span>启用来源</span><strong>{{ coverage.sourceCount }}</strong></div><div class="panel metric"><span>未达目标</span><strong :class="{ danger: insufficient.length }">{{ insufficient.length }}</strong></div></div>
      <div v-if="insufficient.length" class="warning">以下分类未达目标：{{ insufficient.map(item => `${item.name}（${item.itemCount}/${item.required}）`).join('、') }}</div>
      <div class="grids">
        <div class="panel table-panel"><h2>技术知识主题</h2><table><thead><tr><th>主题</th><th>已发布</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="item in coverage.technicalTopics" :key="item.name"><td>{{ item.name }}</td><td>{{ item.itemCount }}</td><td><span :class="item.itemCount >= 25 ? 'ok' : 'bad'">{{ item.itemCount >= 25 ? '达标' : '不足' }}</span></td><td><button class="link" @click="chooseTopic(item.name)">查看内容 ›</button></td></tr></tbody></table></div>
        <div class="panel table-panel"><h2>英语短文难度</h2><table><thead><tr><th>难度</th><th>已发布</th><th>目标</th><th>状态</th></tr></thead><tbody><tr v-for="item in coverage.articleDifficulties" :key="item.name"><td>{{ item.name }}</td><td>{{ item.itemCount }}</td><td>101</td><td><span :class="item.itemCount >= 101 ? 'ok' : 'bad'">{{ item.itemCount >= 101 ? '达标' : '不足' }}</span></td></tr><tr v-if="!coverage.articleDifficulties?.length"><td colspan="4" class="muted">暂无已发布短文</td></tr></tbody></table></div>
      </div>

      <div class="panel knowledge-panel">
        <div class="panel-head"><div><h2>技术知识内容</h2><p>{{ topic ? `当前主题：${topic}` : '全部已发布技术知识' }} · 共 {{ technical.total }} 条</p></div><button v-if="topic" class="plain" @click="chooseTopic('')">清除主题</button></div>
        <div class="toolbar"><form @submit.prevent="search"><input v-model="keywordInput" maxlength="80" placeholder="搜索标题、摘要或正文"/><button>搜索</button><button v-if="keyword" type="button" class="plain" @click="clearSearch">清除</button></form><label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label></div>
        <div v-if="techError" class="inline-error">{{ techError }}</div>
        <div class="content-table"><table><thead><tr><th>标题</th><th>主题</th><th>难度</th><th>来源</th><th>发布日期</th><th></th></tr></thead><tbody><tr v-for="item in technical.items" :key="item.contentId"><td><strong>{{ item.title }}</strong><small>{{ item.summary || '暂无摘要' }}</small></td><td><span v-for="name in item.topics" :key="name" class="topic-tag">{{ name }}</span></td><td>{{ difficulty(item.difficulty) }}</td><td>{{ item.sourceName }}</td><td>{{ date(item.publishedAt) }}</td><td><button class="link" @click="openDetail(item.contentId)">查看详情 ›</button></td></tr><tr v-if="!techLoading && !technical.items.length"><td colspan="6" class="empty">没有符合条件的技术知识</td></tr></tbody></table><div v-if="techLoading" class="loading-mask">正在加载…</div></div>
        <div class="pagination"><span>{{ technical.total ? `${(technical.page-1)*technical.pageSize+1}–${Math.min(technical.total,technical.page*technical.pageSize)} / ${technical.total}` : '0 条' }}</span><button :disabled="page<=1" @click="turn(page-1)">‹</button><span>第 {{ page }} / {{ technical.totalPages || 1 }} 页</span><button :disabled="page>=technical.totalPages" @click="turn(page+1)">›</button></div>
      </div>
    </template>
  </section>

  <div v-if="detailOpen" class="modal-mask" @click.self="closeDetail"><article class="detail-modal"><header><div><span>技术知识详情</span><h2>{{ detail?.title || '正在加载…' }}</h2></div><button @click="closeDetail">×</button></header><div v-if="detailLoading" class="detail-state">正在读取详情…</div><div v-else-if="detailError" class="detail-state error">{{ detailError }}</div><template v-else-if="detail"><div class="detail-meta"><span>{{ difficulty(detail.difficulty) }}</span><span>预计 {{ Math.max(1, Math.ceil(detail.estimatedSeconds / 60)) }} 分钟</span><span>版本 {{ detail.versionNo }}</span><span>{{ detail.reviewStatus === 'approved' ? '已审核' : detail.reviewStatus }}</span></div><div class="detail-topics"><span v-for="name in detail.topics" :key="name">{{ name }}</span></div><section v-if="detail.summary"><h3>摘要</h3><p>{{ detail.summary }}</p></section><section><h3>正文</h3><div class="body">{{ detail.body || '暂无正文' }}</div></section><section class="source-box"><h3>来源与许可</h3><dl><div><dt>内容来源</dt><dd>{{ detail.sourceName }}（{{ detail.sourceType }}）</dd></div><div><dt>原作者</dt><dd>{{ detail.originAuthor || '—' }}</dd></div><div><dt>发布时间</dt><dd>{{ date(detail.publishedAt) }}</dd></div><div><dt>许可快照</dt><dd>{{ detail.licenseSnapshot }}</dd></div></dl><a v-if="detailUrl" :href="detailUrl" target="_blank" rel="noopener noreferrer">打开原始链接 ↗</a></section></template></article></div>
</template>

<style scoped>
.refresh{height:38px;padding:0 18px;border:1px solid #cfdcf0;border-radius:9px;color:#126ff4;background:#fff}.state{margin-top:24px;padding:54px;text-align:center;color:#77849a}.state strong{display:block}.error{color:#c53f4c}.retry{margin-top:14px;padding:8px 16px;border:1px solid #efb8bd;border-radius:7px;color:#b93b47;background:#fff}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:24px}.metric{padding:22px}.metric span{display:block;color:#71809a;font-size:13px}.metric strong{display:block;margin-top:10px;font-size:30px}.danger{color:#d8424f}.warning{margin-top:18px;padding:13px 16px;border:1px solid #f2d59e;border-radius:10px;color:#955e12;background:#fff9ed}.grids{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px;margin-top:20px}.table-panel{padding:22px}.table-panel h2,.knowledge-panel h2{margin:0 0 16px;font-size:18px}table{width:100%;border-collapse:collapse}th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left}th{color:#75819a;font-size:12px;font-weight:650}.ok,.bad{display:inline-block;padding:4px 10px;border-radius:99px;font-size:12px}.ok{color:#15845f;background:#e8f8f1}.bad{color:#c43a47;background:#fff0f1}.muted,.empty{color:#8792a7;text-align:center}.link{padding:4px;border:0;color:#0d6ff5;background:transparent;white-space:nowrap}.knowledge-panel{margin-top:20px;padding:22px}.panel-head,.toolbar,.pagination{display:flex;align-items:center;justify-content:space-between}.panel-head h2{margin-bottom:5px}.panel-head p{margin:0;color:#7a869d;font-size:12px}.plain{border:1px solid #d7dfeb!important;color:#62718c!important;background:#fff!important}.toolbar{margin:18px 0 13px}.toolbar form{display:flex;gap:8px}.toolbar input{width:320px;height:38px;padding:0 11px;border:1px solid #d7dfeb;border-radius:8px}.toolbar button{height:38px;padding:0 16px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}.toolbar label{color:#6c7890;font-size:12px}.toolbar select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}.content-table{position:relative;min-height:180px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}.content-table th{background:#f8faff}.content-table td{font-size:12px;color:#53617a}.content-table td strong,.content-table td small{display:block}.content-table td strong{color:#11204a;font-size:14px}.content-table td small{max-width:360px;margin-top:5px;overflow:hidden;color:#7c889d;text-overflow:ellipsis;white-space:nowrap}.topic-tag,.detail-topics span{display:inline-block;margin:2px 4px 2px 0;padding:3px 7px;border-radius:6px;color:#176fdb;background:#eaf3ff}.loading-mask{position:absolute;inset:41px 0 0;display:grid;place-items:center;background:rgba(255,255,255,.86);color:#71809b}.inline-error{margin-bottom:12px;color:#c53f4c}.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}.pagination button:disabled{color:#bbc3cf}.detail-modal{width:min(820px,88vw);max-height:88vh;overflow:auto;border-radius:16px;background:#fff;box-shadow:0 25px 70px rgba(19,36,70,.24)}.detail-modal header{position:sticky;top:0;z-index:2;display:flex;justify-content:space-between;padding:24px 28px;border-bottom:1px solid #e8edf4;background:#fff}.detail-modal header span{color:#72809a;font-size:12px}.detail-modal header h2{margin:6px 0 0}.detail-modal header button{border:0;background:transparent;color:#6f7d95;font-size:28px}.detail-state{padding:80px;text-align:center;color:#73819a}.detail-meta,.detail-topics,.detail-modal>section{margin:20px 28px}.detail-meta{display:flex;gap:8px}.detail-meta span{padding:5px 9px;border-radius:6px;color:#566681;background:#f1f4f8;font-size:12px}.detail-modal section h3{margin:0 0 10px;font-size:15px}.detail-modal section p,.body{color:#3e4d69;line-height:1.8}.body{white-space:pre-wrap}.source-box{padding:18px;border-radius:10px;background:#f7f9fc}.source-box dl{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.source-box dl div{min-width:0}.source-box dt{color:#8490a4;font-size:11px}.source-box dd{margin:5px 0 0;overflow-wrap:anywhere;color:#42516d;font-size:13px}.source-box a{display:inline-block;margin-top:14px;color:#0d6ff5;text-decoration:none}@media(max-width:1100px){.grids{grid-template-columns:1fr}.metrics{grid-template-columns:1fr 1fr}.source-box dl{grid-template-columns:1fr}}
.body{user-select:text}
</style>
