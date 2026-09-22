<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'
import { showError } from '../../services/notification.js'

const books = ref([])
const selectedBookId = ref('')
const result = ref({ book: null, total: 0, page: 1, pageSize: 20, totalPages: 0, items: [] })
const page = ref(1)
const pageSize = ref(20)
const jumpPage = ref(1)
const keywordInput = ref('')
const keyword = ref('')
const stage = ref('')
const STAGE_LABELS = { primary:'小学',junior:'初中',senior:'高中',cet4:'四级',cet6:'六级',postgrad:'考研',ielts:'雅思',toefl:'托福',gre:'GRE',computer:'计算机' }
const stageOptions = [{ value:'', label:'全部学段' }].concat(Object.keys(STAGE_LABELS).map(key => ({ value:key, label:STAGE_LABELS[key] }))).concat([{ value:'unclassified', label:'未分级' }])
const loading = ref(true)
const error = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const speechLoading = ref(false)
let loadVersion = 0
let currentAudio = null

const selectedBook = computed(() => books.value.find(item => item.bookId === selectedBookId.value) || result.value.book)
const sourceLink = computed(() => safeUrl(detail.value?.originUrl || detail.value?.sourceUrl))
const rangeText = computed(() => {
  if (!result.value.total) return '0 条'
  const start = (result.value.page - 1) * result.value.pageSize + 1
  const end = Math.min(result.value.total, result.value.page * result.value.pageSize)
  return `${start}–${end} / ${result.value.total} 条`
})

async function loadBooks(keepSelection = true) {
  error.value = ''
  try {
    books.value = await request('/admin/vocabulary-books')
    if (!keepSelection || !books.value.some(item => item.bookId === selectedBookId.value)) selectedBookId.value = books.value[0]?.bookId || ''
    if (selectedBookId.value) await loadWords()
    else { result.value = { book: null, total: 0, page: 1, pageSize: pageSize.value, totalPages: 0, items: [] }; loading.value = false }
  } catch (e) { error.value = e.message || '词书加载失败'; loading.value = false }
}

async function loadWords() {
  if (!selectedBookId.value) return
  const version = ++loadVersion
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), pageSize: String(pageSize.value) })
    if (keyword.value) params.set('keyword', keyword.value)
    if (stage.value) params.set('stage', stage.value)
    const data = await request(`/admin/vocabulary-books/${encodeURIComponent(selectedBookId.value)}/words?${params}`)
    if (version !== loadVersion) return
    result.value = data; page.value = data.page; jumpPage.value = data.page
  } catch (e) { if (version === loadVersion) error.value = e.message || '单词列表加载失败' }
  finally { if (version === loadVersion) loading.value = false }
}

function chooseBook(id) { selectedBookId.value = id; page.value = 1; keyword.value = ''; keywordInput.value = ''; loadWords() }
function chooseBookEvent(event) { chooseBook(event.target.value) }
function search() { keyword.value = keywordInput.value.trim(); page.value = 1; loadWords() }
function changeStage() { page.value = 1; loadWords() }
function clearSearch() { keywordInput.value = ''; keyword.value = ''; page.value = 1; loadWords() }
function turn(target) { if (target < 1 || target > result.value.totalPages || target === page.value) return; page.value = target; loadWords() }
function jump() { const max = Math.max(1, result.value.totalPages); const target = Math.max(1, Math.min(max, Number(jumpPage.value) || 1)); turn(target); jumpPage.value = target }
async function openDetail(contentId) {
  if (!contentId) return
  detailOpen.value = true; detailLoading.value = true; detailError.value = ''; detail.value = null
  try { detail.value = await request(`/admin/vocabulary-books/words/${encodeURIComponent(contentId)}`) }
  catch (e) { detailError.value = e.message || '单词详情加载失败' }
  finally { detailLoading.value = false }
}
function closeDetail() { if (currentAudio) { currentAudio.pause(); currentAudio = null }; detailOpen.value = false; detail.value = null; detailError.value = '' }
function safeUrl(value, allowBlob = false) {
  if (!value) return ''
  try {
    const parsed = new URL(value, window.location.origin)
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:' || (allowBlob && parsed.protocol === 'blob:')) return parsed.href
  } catch { /* invalid URL */ }
  return ''
}
async function playAudio(url) {
  const playable = safeUrl(url, true)
  if (!playable) { detailError.value = '音频地址无效或协议不受支持'; showError(new Error(detailError.value)); return }
  try { if (currentAudio) currentAudio.pause(); currentAudio = new Audio(playable); await currentAudio.play() }
  catch { detailError.value = '音频播放失败，请检查媒体服务或浏览器权限'; showError(new Error(detailError.value)) }
}
async function generateSpeech() {
  if (!detail.value?.contentId || speechLoading.value) return
  speechLoading.value = true; detailError.value = ''
  try { const data = await request(`/admin/vocabulary-books/words/${encodeURIComponent(detail.value.contentId)}/speech`, { method:'POST' }); await playAudio(data.audioUrl) }
  catch (e) { detailError.value = e.message || '发音生成失败' }
  finally { speechLoading.value = false }
}
function stageText(value) { return STAGE_LABELS[value] || value || '未分级' }
watch(pageSize, () => { page.value = 1; loadWords() })
onMounted(() => loadBooks(false))
onBeforeUnmount(() => { if (currentAudio) { currentAudio.pause(); currentAudio = null } })
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>英语单词</h1><p>按词书查看成员、发布状态和审核状态；成员数以词书关系表实时统计为准。</p></div><button class="refresh" @click="loadBooks">刷新数据</button></div>
    <div class="panel book-picker"><label><span>词书</span><select :value="selectedBookId" @change="chooseBookEvent"><option v-for="book in books" :key="book.bookId" :value="book.bookId">{{ book.bookName }}</option></select></label><p v-if="selectedBook">{{ selectedBook.levelCode || selectedBook.bookType }} · 成员 {{ selectedBook.memberCount }} · 可用 {{ selectedBook.availableCount }}</p><p v-else>暂无启用词书</p></div>

    <div v-if="selectedBook" class="panel summary-bar">
      <div><span>当前词书</span><strong>{{ selectedBook.bookName }}</strong></div>
      <div><span>成员总数</span><strong>{{ selectedBook.memberCount }}</strong></div>
      <div><span>已发布且审核通过</span><strong>{{ selectedBook.availableCount }}</strong></div>
      <div><span>数据库标称数</span><strong :class="{warn:selectedBook.countMismatch}">{{ selectedBook.declaredWordCount }}</strong></div>
    </div>

    <div class="panel word-panel">
      <div class="toolbar">
        <form class="search" @submit.prevent="search"><input v-model="keywordInput" maxlength="80" placeholder="搜索英文或中文释义"/><button>搜索</button><button v-if="keyword" type="button" class="plain" @click="clearSearch">清除</button></form>
        <div class="filters"><label>学段 <select v-model="stage" @change="changeStage"><option v-for="opt in stageOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option></select></label><label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select> 个</label></div>
      </div>
      <div class="table-wrap">
        <table><thead><tr><th>序号</th><th>单词</th><th>音标</th><th>释义</th><th>学段</th><th>成员属性</th><th>内容状态</th><th>操作</th></tr></thead>
          <tbody><tr v-for="(item, index) in result.items" :key="item.contentId || `${item.sortNo}-${item.wordTerm}`"><td class="row-index">{{ (page - 1) * pageSize + index + 1 }}</td><td><strong>{{ item.wordTerm || '内容缺失' }}</strong></td><td class="phonetic">{{ item.phonetic || '—' }}</td><td>{{ item.meaning || '—' }}</td><td>{{ stageText(item.stage) }}</td><td><span v-if="item.core" class="tag core">核心</span><span class="tag">权重 {{ item.importance }}</span></td><td><span :class="['state-pill',item.contentState==='published'&&item.reviewStatus==='approved'?'ok':'pending']">{{ item.contentState==='published'&&item.reviewStatus==='approved'?'可用':`${item.contentState || '缺失'} / ${item.reviewStatus || '未审核'}` }}</span></td><td><button class="detail-link" :disabled="!item.contentId" @click="openDetail(item.contentId)">查看详情</button></td></tr><tr v-if="!loading && !result.items.length"><td colspan="8" class="empty">{{ keyword ? '没有匹配的单词' : '该词书暂无成员' }}</td></tr></tbody>
        </table>
        <div v-if="loading" class="loading">正在读取单词…</div>
      </div>
      <div class="pagination"><span>{{ rangeText }}</span><button :disabled="page<=1" @click="turn(page-1)">‹</button><span>第</span><input v-model.number="jumpPage" type="number" min="1" :max="Math.max(1,result.totalPages)" @change="jump"/><span>/ {{ result.totalPages || 1 }} 页</span><button :disabled="page>=result.totalPages" @click="turn(page+1)">›</button></div>
    </div>
  </section>

  <div v-if="detailOpen" class="modal-mask" @click.self="closeDetail">
    <article class="word-detail">
      <header><div><small>单词详情</small><h2>{{ detail?.wordTerm || '正在加载…' }}</h2><p v-if="detail">{{ detail.phonetic || '暂无音标' }}</p></div><button @click="closeDetail">×</button></header>
      <div v-if="detailLoading" class="detail-state">正在读取单词…</div>
      <div v-else-if="detailError && !detail" class="detail-state">详情未加载，请关闭后重试</div>
      <template v-else-if="detail">
        <div class="word-meta"><span>{{ stageText(detail.stage) }}</span><span>{{ detail.difficulty === 'advanced' ? '进阶' : '入门' }}</span><span>{{ detail.sourceName || '未标注来源' }}</span></div>
        <section class="pronunciation-section"><div><h3>音标与发音</h3><p>{{ detail.phonetic || '暂无音标' }}</p></div><div class="audio-actions"><button v-for="(audio,index) in detail.pronunciations" :key="`${audio.accent}-${index}`" @click="playAudio(audio.audioUrl)">▶ {{ (audio.accent || '发音').toUpperCase() }} {{ audio.phonetic || '' }}</button><button v-if="!detail.pronunciations?.length" :disabled="speechLoading" @click="generateSpeech">{{ speechLoading ? '生成中…' : '▶ 生成并播放' }}</button></div></section>
        <section><h3>基础释义</h3><p class="meaning">{{ detail.meaning || '暂无释义' }}</p></section>
        <section v-if="detail.exampleText"><h3>例句</h3><div class="example"><strong>{{ detail.exampleText }}</strong><span>{{ detail.exampleTranslation || '暂无翻译' }}</span></div></section>
        <section v-if="detail.senses?.length"><h3>词义与例句</h3><div v-for="sense in detail.senses" :key="sense.id" class="sense"><div class="sense-title"><b>{{ sense.partOfSpeech }}</b><span>{{ sense.meaning }}</span><button v-for="(audio,index) in sense.pronunciations" :key="index" @click="playAudio(audio.audioUrl)">▶ {{ audio.accent }}</button></div><div v-for="example in sense.examples" :key="example.id" class="example"><strong>{{ example.sentence }}</strong><span>{{ example.translation }}</span><button v-for="(audio,index) in example.pronunciations" :key="index" @click="playAudio(audio.audioUrl)">▶ 例句</button></div></div></section>
        <section class="source-section"><h3>来源与许可</h3><p>{{ detail.sourceName || '—' }}（{{ detail.sourceType || '—' }}）</p><p>{{ detail.licenseSnapshot || '未记录许可' }}</p><a v-if="sourceLink" :href="sourceLink" target="_blank" rel="noopener noreferrer">打开原始链接 ↗</a></section>
      </template>
    </article>
  </div>
</template>

<style scoped>
.refresh{height:40px;padding:0 18px;border:1px solid #c9daf3;border-radius:9px;color:#126ff4;background:#fff}.notice{margin-top:18px;padding:13px 16px;border:1px solid #efc7cb;border-radius:10px;color:#b83c48;background:#fff4f5}.book-strip{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-top:24px}.book-card{min-height:126px;padding:18px;border:1px solid #dce4ef;border-radius:13px;background:#fff;text-align:left;box-shadow:0 7px 20px rgba(44,72,118,.04)}.book-card.active{border-color:#3d8cff;background:linear-gradient(145deg,#fff,#eef5ff);box-shadow:0 9px 26px rgba(32,113,235,.12)}.book-card span,.book-card strong,.book-card small,.book-card em{display:block}.book-card span{color:#1473f7;font-size:11px;text-transform:uppercase}.book-card strong{margin-top:8px;color:#12214a;font-size:17px}.book-card small{margin-top:10px;color:#6f7d96}.book-card em{margin-top:7px;color:#c37917;font-size:11px;font-style:normal}.empty-book{grid-column:1/-1;padding:50px;text-align:center;color:#7a869b}.summary-bar{display:grid;grid-template-columns:2fr repeat(3,1fr);gap:1px;margin-top:18px;overflow:hidden}.summary-bar div{padding:18px 22px;border-right:1px solid #edf1f6}.summary-bar div:last-child{border:0}.summary-bar span,.summary-bar strong{display:block}.summary-bar span{color:#7a879d;font-size:12px}.summary-bar strong{margin-top:7px;font-size:20px}.summary-bar .warn{color:#d48318}.word-panel{margin-top:18px;padding:18px}.toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:15px}.filters{display:flex;align-items:center;gap:14px}.search{display:flex;gap:8px}.search input{width:300px;height:39px;padding:0 12px;border:1px solid #d5deeb;border-radius:8px}.search button{height:39px;padding:0 17px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}.search .plain{border:1px solid #d8e0eb;color:#61708b;background:#fff}.toolbar label{color:#66738d;font-size:12px}.toolbar select{height:35px;margin:0 6px;padding:0 25px 0 9px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}.table-wrap{position:relative;min-height:220px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}table{width:100%;border-collapse:collapse;font-size:12px;white-space:nowrap}th{height:42px;padding:0 13px;background:#f8faff;color:#68758e;text-align:left}td{height:48px;padding:0 13px;border-top:1px solid #e9edf3;color:#52617c}td strong{color:#12204a;font-size:14px}.row-index{color:#8b96a8;font-variant-numeric:tabular-nums}.phonetic{color:#7886a1}.tag{display:inline-block;margin-right:5px;padding:3px 7px;border-radius:6px;color:#60708c;background:#f0f3f7}.tag.core{color:#176fdc;background:#eaf3ff}.state-pill{display:inline-block;padding:4px 9px;border-radius:99px}.state-pill.ok{color:#0b8861;background:#e7f8f1}.state-pill.pending{color:#b16e18;background:#fff5e6}.empty{height:170px;text-align:center;color:#8994a7}.loading{position:absolute;inset:42px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}.pagination{display:flex;align-items:center;justify-content:flex-end;gap:9px;margin-top:15px;color:#6e7b94;font-size:12px}.pagination button{width:31px;height:29px;border:1px solid #d7e0ec;border-radius:7px;color:#126ff4;background:#fff}.pagination button:disabled{color:#bdc5d0}.pagination input{width:55px;height:29px;padding:0 5px;border:1px solid #d7e0ec;border-radius:7px;text-align:center}@media(max-width:1100px){.book-strip{grid-template-columns:repeat(2,minmax(0,1fr))}.summary-bar{grid-template-columns:repeat(2,1fr)}}
.book-picker{display:flex;align-items:end;gap:22px;margin-top:24px;padding:18px 22px}.book-picker label{display:grid;gap:7px;min-width:280px;color:#71809a;font-size:12px}.book-picker select{height:38px;padding:0 34px 0 11px;border:1px solid #d7dfeb;border-radius:8px;background:#fff;color:#17244a}.book-picker p{margin:0 0 10px;color:#71809a;font-size:12px}
.detail-link{border:0;color:#0d6ff5;background:transparent}.detail-link:disabled{color:#aeb7c5}.word-detail{width:min(760px,88vw);max-height:88vh;overflow:auto;border-radius:16px;background:#fff;box-shadow:0 28px 80px rgba(14,34,73,.25)}.word-detail header{position:sticky;top:0;z-index:2;display:flex;justify-content:space-between;padding:24px 28px;border-bottom:1px solid #e7ecf3;background:#fff}.word-detail header small{color:#72809a}.word-detail header h2{margin:5px 0;color:#101f49;font-size:30px}.word-detail header p{margin:0;color:#74819a}.word-detail header>button{border:0;color:#6d7b94;background:transparent;font-size:28px}.detail-state{padding:80px;text-align:center;color:#75829a}.detail-state.error,.detail-error{color:#be3e4a}.detail-error{margin:18px 28px 0;padding:11px 13px;border-radius:8px;background:#fff1f2}.word-meta{display:flex;gap:8px;margin:18px 28px}.word-meta span{padding:5px 9px;border-radius:6px;color:#4d607f;background:#f1f4f8;font-size:12px}.word-detail section{margin:18px 28px;padding-top:17px;border-top:1px solid #edf0f5}.word-detail section h3{margin:0 0 10px;font-size:15px}.pronunciation-section{display:flex;align-items:center;justify-content:space-between;gap:18px}.pronunciation-section p{margin:6px 0 0;color:#71809a}.audio-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:7px}.audio-actions button,.sense button,.example button{padding:7px 10px;border:1px solid #bdd5fa;border-radius:7px;color:#0e6ef3;background:#fff}.meaning{color:#334564;line-height:1.7}.sense{padding:14px 0;border-top:1px dashed #dce3ee}.sense:first-of-type{border:0}.sense-title{display:flex;align-items:center;gap:9px}.sense-title b{padding:3px 7px;border-radius:5px;color:#704bd6;background:#f1ecff}.sense-title span{flex:1;color:#31415f}.example{position:relative;margin-top:10px;padding:13px 100px 13px 14px;border-radius:9px;background:#f7f9fc}.example strong,.example span{display:block}.example strong{color:#21314f}.example span{margin-top:5px;color:#75829a}.example button{position:absolute;right:12px;top:50%;transform:translateY(-50%)}.source-section{padding:16px!important;border:0!important;border-radius:10px;background:#f7f9fc}.source-section p{margin:6px 0;color:#5b6982}.source-section a{display:inline-block;margin-top:8px;color:#0d6ff5;text-decoration:none}
</style>
