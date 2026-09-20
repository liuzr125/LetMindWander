<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'

const onlineBooks = ref([])
const onlineCategory = ref('core')
const syncingBook = ref('')
const previewBook = ref(null)
const onlineWords = ref({ items:[], total:0, page:1, pageSize:20 })
const wordKeyword = ref('')
const onlineLoading = ref(false)
const onlinePage = ref(1)
const onlinePageSize = ref(5)
const filteredOnlineBooks = computed(() => onlineBooks.value.filter(b => b.category === onlineCategory.value))
const onlinePageCount = computed(() => Math.max(1, Math.ceil(filteredOnlineBooks.value.length / onlinePageSize.value)))
const displayedOnlineBooks = computed(() => {
  const start = (onlinePage.value - 1) * onlinePageSize.value
  return filteredOnlineBooks.value.slice(start, start + onlinePageSize.value)
})
const selectedSource = computed(() => datasets.value.find(d => d.datasetId === batchForm.value.datasetId))
const datasets = ref([])
const books = ref([])
const batches = ref([])
const voiceOptions = ref([])
const selected = ref(null)
const loading = ref(false)
const message = ref('')
const error = ref('')
const showDataset = ref(false)
const showBatch = ref(false)
const deletingDataset = ref('')
const datasetForm = ref({ datasetName:'', providerName:'', licenseStatus:'metadata_only', licenseNote:'', payload:'word,pos,meaning,example,phonetic\nexample,noun,例子,This is an example.,/ɪɡˈzɑːmpəl/' })
const batchForm = ref({ datasetId:'', targetBookId:'', usVoice:'eva', ukVoice:'luna', sampleRate:16000, exportSql:true, directPublish:false })
const selectedReviewIds = ref([])
const previewingVoice = ref('')
const dataSourcesSection = ref(null)
let previewAudio = null

const currentItems = computed(() => selected.value?.items || [])
const pendingReview = computed(() => currentItems.value.filter(item => item.decision === 'review'))
const usVoices = computed(() => voiceOptions.value.filter(voice => voice.accent === 'us'))
const ukVoices = computed(() => voiceOptions.value.filter(voice => voice.accent === 'uk'))

function flash(text) { message.value = text; error.value = ''; window.setTimeout(() => { if (message.value === text) message.value = '' }, 3600) }
function failure(e) { error.value = e.message || '操作失败'; message.value = '' }
function stateText(value) { return ({draft:'草稿',running:'解析中',paused:'已暂停',failed:'失败',review_required:'待人工审核',ready_for_review:'待生成音频',tts_failed:'音频任务失败',ready_to_publish:'音频完成（待发布门禁）',published:'已发布'}[value]) || value || '—' }
function licenseText(value) { return ({verified:'已核验',metadata_only:'仅元数据',unknown:'未知',restricted:'受限'}[value]) || value || '未知' }
function choiceText(value) { return ({create:'新建',reuse:'复用',review:'待审核',skip:'跳过'}[value]) || value || '—' }
function date(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12:false }) : '—' }
function goOnlinePage(page) { onlinePage.value = Math.min(Math.max(1, page), onlinePageCount.value) }
function viewDataSources() { dataSourcesSection.value?.scrollIntoView({ behavior:'smooth', block:'start' }) }

async function loadAll() {
  loading.value = true; error.value = ''
  try {
    const [nextDatasets, nextBooks, nextBatches, nextVoices] = await Promise.all([request('/admin/vocabulary/datasets'), request('/admin/vocabulary-books'), request('/admin/vocabulary/import-batches'), request('/admin/vocabulary/tts/voices')])
    datasets.value = nextDatasets; books.value = nextBooks; batches.value = nextBatches; voiceOptions.value = nextVoices
    onlineBooks.value = await request('/admin/vocabulary/online-books')
    goOnlinePage(onlinePage.value)
  } catch (e) { failure(e) } finally { loading.value = false }
}
function openNewBatch() { batchForm.value.datasetId=''; batchForm.value.targetBookId=''; showBatch.value=true }
function useOnline(book) { batchForm.value.datasetId=book.latestDatasetId; batchForm.value.targetBookId=''; showBatch.value=true }
async function syncOnline(book) {
  if(syncingBook.value) return
  syncingBook.value=book.bookCode
  try {
    const result=await request('/admin/vocabulary/online-books/'+encodeURIComponent(book.bookCode)+'/sync',{method:'POST'})
    await loadAll()
    flash((result.reusedSnapshot?'已复用相同快照':'已下载并保存快照')+'：'+result.itemCount+' 行；未发布、未生成音频')
  } catch(e) { failure(e) } finally { syncingBook.value='' }
}
function quality(book) { try { return JSON.parse(book.qualitySummary || '{}') } catch { return {} } }
function wordData(word) { try { return JSON.parse(word.normalized_json) } catch { return {} } }
async function openOnlineWords(book, page=1) {
  onlineLoading.value=true
  if(previewBook.value?.bookCode!==book.bookCode) wordKeyword.value=''
  previewBook.value=book
  try { onlineWords.value=await request('/admin/vocabulary/online-books/'+encodeURIComponent(book.bookCode)+'/words?page='+page+'&pageSize=20&keyword='+encodeURIComponent(wordKeyword.value)) }
  catch(e) { failure(e); onlineWords.value={items:[],total:0,page:1,pageSize:20} }
  finally { onlineLoading.value=false }
}
async function openBatch(id) { try { selected.value = await request(`/admin/vocabulary/import-batches/${encodeURIComponent(id)}`); selectedReviewIds.value = [] } catch (e) { failure(e) } }
function closeBatchDetail() { selected.value=null; selectedReviewIds.value=[] }
async function saveDataset() {
  try { await request('/admin/vocabulary/datasets', { method:'POST', body:JSON.stringify(datasetForm.value) }); showDataset.value=false; await loadAll(); flash('数据源已保存，可用于创建暂存批次') } catch (e) { failure(e) }
}
async function deleteDataset(source) {
  if (deletingDataset.value) return
  const onlineWarning=source.sourceType==='online_snapshot' ? '该在线快照的词条也会一并删除，并需要重新同步后才能再次使用。' : '原始上传内容会被永久删除。'
  if (!window.confirm(`确定删除“${source.datasetName}”吗？\n${onlineWarning}\n已被批次引用的数据源不能删除。`)) return
  deletingDataset.value=source.datasetId
  try {
    const result=await request(`/admin/vocabulary/datasets/${encodeURIComponent(source.datasetId)}`,{method:'DELETE'})
    if(batchForm.value.datasetId===source.datasetId)batchForm.value.datasetId=''
    if(selected.value?.datasetId===source.datasetId)selected.value=null
    await loadAll()
    flash(`已删除数据源“${result.datasetName}”${result.removedOnlineWords?`及 ${result.removedOnlineWords} 条在线词条`:''}`)
  } catch(e) { failure(e) } finally { deletingDataset.value='' }
}
async function createBatch() {
  if(!batchForm.value.datasetId || !batchForm.value.targetBookId) { failure(new Error("请明确选择数据源和目标词书")); return }
  try {
    const body = { datasetId:batchForm.value.datasetId, targetBookId:batchForm.value.targetBookId, options:{ usVoice:batchForm.value.usVoice, ukVoice:batchForm.value.ukVoice, sampleRate:batchForm.value.sampleRate, exportSql:batchForm.value.exportSql, directPublish:batchForm.value.directPublish } }
    const batch = await request('/admin/vocabulary/import-batches', { method:'POST', body:JSON.stringify(body) })
    closeBatch(); await loadAll(); await openBatch(batch.batchId); flash('批次已创建，参数快照已锁定')
  } catch (e) { failure(e) }
}
function stopPreview() { if (previewAudio) { previewAudio.pause(); previewAudio = null } }
function closeBatch() { stopPreview(); showBatch.value=false }
async function previewVoice(voice) { if (!voice || previewingVoice.value) return; previewingVoice.value=voice; error.value=''; try { const result=await request('/admin/vocabulary/tts/preview',{method:'POST',body:JSON.stringify({voice,sampleRate:batchForm.value.sampleRate})}); stopPreview(); previewAudio=new Audio(result.audioDataUrl); await previewAudio.play() } catch(e) { failure(e) } finally { previewingVoice.value='' } }
async function act(path, success) { if (!selected.value) return; try { selected.value = await request(`/admin/vocabulary/import-batches/${encodeURIComponent(selected.value.batchId)}${path}`, { method:'POST' }); await loadAll(); flash(success) } catch (e) { failure(e) } }
async function review(decision) { if (!selectedReviewIds.value.length) return; try { selected.value = await request('/admin/vocabulary/import-items/review', { method:'POST', body:JSON.stringify({ batchId:selected.value.batchId, itemIds:selectedReviewIds.value, decision }) }); selectedReviewIds.value=[]; await loadAll(); flash('审核结果已保存') } catch(e) { failure(e) } }
function canStart() { return ['draft','paused','failed'].includes(selected.value?.state) }

watch([onlineCategory, onlinePageSize], () => { onlinePage.value = 1 })
onMounted(loadAll)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>词库批次自动化</h1><p>数据源、清洗碰撞、人工审核与语音任务全部先进入暂存层；未核验许可或未完成门禁不能发布。</p></div><div class="heading-actions"><button class="refresh" @click="loadAll">刷新</button><button class="primary" @click="showDataset=true">录入数据源</button><button class="primary" @click="openNewBatch">新建批次</button></div></div>
    <p v-if="message" class="notice success">{{ message }}</p>
    <p v-if="error" class="notice">{{ error }}</p>
    <div class="panel source-panel">
      <div class="panel-title"><div><h2>在线词书目录</h2><p>核心书目与“英语单词”页面保持一致；具体公开词表按固定来源版本保存，不能冒充现行教材或当年考试大纲。下载不产生语音费用。</p></div>
        <select v-model="onlineCategory"><option value="core">核心词书</option><option value="alternative">其他候选词表</option><option value="popular">热门教材书目</option></select>
      </div>
      <div class="table-wrap"><table><thead><tr><th>书名 / 版本</th><th>来源</th><th>条目数（目录 / 已下载）</th><th>质量 / 授权</th><th>操作</th></tr></thead><tbody>
        <tr v-for="book in displayedOnlineBooks" :key="book.bookCode">
          <td><strong>{{ book.bookName }}</strong><small>{{ book.editionLabel }}</small><small v-if="book.sourceRevision">来源版本 {{ book.sourceRevision.slice(0,12) }}（非考试年份）</small></td>
          <td><a :href="book.sourceUrl" target="_blank" rel="noopener noreferrer">{{ book.providerName }}</a></td>
          <td>{{ book.expectedCount ?? '未核实' }} / {{ book.actualCount }}</td>
          <td><span class="pill warn">{{ licenseText(book.licenseStatus) }}</span><small v-if="book.latestDatasetId">缺音标 {{ quality(book).missingPhonetic }} · 缺例句 {{ quality(book).missingExample }} · 待映射 {{ quality(book).needsSenseMapping }}</small><small>{{ book.licenseNote }}</small></td>
          <td><template v-if="book.downloadable">
            <button class="link" :disabled="!!syncingBook" @click="syncOnline(book)">{{ syncingBook===book.bookCode?'下载校验中…':book.latestDatasetId?'校验 / 同步':'下载词表' }}</button>
            <button v-if="book.latestDatasetId" class="link" @click="openOnlineWords(book)">查看词条</button>
            <button v-if="book.latestDatasetId" class="link" @click="useOnline(book)">选择此词表</button>
          </template><span v-else class="pill warn">待提供对应授权词表</span></td>
        </tr>
        <tr v-if="!filteredOnlineBooks.length"><td colspan="5" class="empty">暂无目录，请确认 V3.16.4 迁移已执行并重启后端</td></tr>
      </tbody></table></div>
      <div v-if="filteredOnlineBooks.length" class="catalog-pagination">
        <span>共 {{ filteredOnlineBooks.length }} 本 · 第 {{ onlinePage }} / {{ onlinePageCount }} 页</span>
        <div>
          <label>每页
            <select v-model.number="onlinePageSize"><option :value="5">5</option><option :value="10">10</option><option :value="20">20</option></select>
            本
          </label>
          <button :disabled="onlinePage <= 1" @click="goOnlinePage(onlinePage - 1)">上一页</button>
          <button :disabled="onlinePage >= onlinePageCount" @click="goOnlinePage(onlinePage + 1)">下一页</button>
        </div>
      </div>
    </div>

    <div class="metric-grid"><button class="panel metric metric-link" type="button" @click="viewDataSources"><span>可用数据源</span><strong>{{ datasets.length }}</strong><small>点击查看数据源目录 ↓</small></button><div class="panel metric"><span>已核验数据源</span><strong>{{ datasets.filter(x=>x.licenseStatus==='verified').length }}</strong><small>可进入正式发布门禁</small></div><div class="panel metric"><span>最近批次</span><strong>{{ batches.length }}</strong><small>保留最近 100 条</small></div><div class="panel metric"><span>待审核项</span><strong>{{ batches.reduce((sum,x)=>sum+(x.reviewCount||0),0) }}</strong><small>需人工确认后继续</small></div></div>

    <div ref="dataSourcesSection" class="panel source-panel data-sources-section"><div class="panel-title"><div><h2>数据源目录</h2><p>授权状态是正式发布的前置条件。删除前会确认；已被批次使用的数据源将被保留，避免破坏历史快照。</p></div></div><div class="table-wrap"><table><thead><tr><th>名称</th><th>提供方</th><th>版本</th><th>格式</th><th>词条数</th><th>许可</th><th>最近更新</th><th>操作</th></tr></thead><tbody><tr v-for="source in datasets" :key="source.datasetId"><td><strong>{{ source.datasetName }}</strong><small>{{ source.datasetCode }}</small></td><td>{{ source.providerName }}</td><td>{{ source.versionLabel }}</td><td>{{ source.dataFormat }}</td><td>{{ source.itemCount }}</td><td><span :class="['pill',source.licenseStatus==='verified'?'ok':'warn']">{{ licenseText(source.licenseStatus) }}</span></td><td>{{ date(source.updatedAt) }}</td><td><button class="link delete-link" :disabled="!!deletingDataset" @click="deleteDataset(source)">{{ deletingDataset===source.datasetId?'删除中…':'删除' }}</button></td></tr><tr v-if="!datasets.length"><td colspan="8" class="empty">还没有录入数据源</td></tr></tbody></table></div></div>

    <div class="panel batch-panel"><div class="panel-title"><div><h2>批次执行中心</h2><p>启动批次会解析、规范化、识别重复词，并生成稳定 object_key 的语音任务计划。</p></div></div><div class="table-wrap"><table><thead><tr><th>批次</th><th>目标词书</th><th>状态</th><th>步骤</th><th>结果</th><th>更新时间</th><th></th></tr></thead><tbody><tr v-for="batch in batches" :key="batch.batchId"><td><strong>{{ batch.datasetName }}</strong><small>{{ batch.batchId.slice(0,12) }}</small></td><td>{{ batch.targetBookName }}</td><td><span :class="['pill',batch.state==='published'?'ok':batch.state==='failed'?'danger':'warn']">{{ stateText(batch.state) }}</span></td><td>{{ batch.currentStep }}</td><td>成功 {{ batch.successCount }} · 新建 {{ batch.createdCount }} · 复用 {{ batch.reusedCount }} · 待审 {{ batch.reviewCount }}</td><td>{{ date(batch.updatedAt) }}</td><td><button class="link" @click="openBatch(batch.batchId)">查看</button></td></tr><tr v-if="!batches.length"><td colspan="7" class="empty">尚未创建批次</td></tr></tbody></table></div></div>

    <div v-if="selected" class="modal-mask batch-detail-mask" @click.self="closeBatchDetail"><div class="dialog batch-detail-dialog"><div class="detail-panel"><div class="detail-head"><div><span class="eyebrow">批次详情</span><h2>{{ selected.datasetName }} → {{ selected.targetBookName }}</h2><p>{{ selected.batchId }}</p></div><div class="detail-actions"><button v-if="canStart()" class="primary" @click="act('/start','解析和语音任务计划已生成')">启动 / 重新开始</button><button v-if="['ready_for_review','tts_failed'].includes(selected.state)" :disabled="selected.licenseStatus!=='verified'" class="primary" @click="act('/retry','待生成或失败音频已提交')">生成 / 重试音频（每次最多 10 条）</button><button v-if="['ready_for_review','review_required','running'].includes(selected.state)" @click="act('/pause','批次已暂停')">暂停</button><button :disabled="!['ready_for_review','ready_to_publish','tts_failed'].includes(selected.state) || !selected.totalCount || selected.licenseStatus!=='verified' || selected.ttsPending || selected.ttsFailed || selected.reviewCount" @click="act('/publish','已通过发布门禁')">正式发布</button><button @click="closeBatchDetail">关闭</button></div></div>
      <div class="stats"><span><b>{{ stateText(selected.state) }}</b>当前状态</span><span><b>{{ selected.totalCount }}</b>来源行</span><span><b>{{ selected.successCount }}</b>可处理</span><span><b>{{ selected.ttsPending }}</b>待生成音频</span><span><b>{{ selected.ttsFailed }}</b>失败音频</span></div>
      <p v-if="selected.lastErrorMessage" class="notice">{{ selected.lastErrorCode }}：{{ selected.lastErrorMessage }}</p>
      <div v-if="pendingReview.length" class="review-bar"><div><strong>人工审核</strong><span>已选择 {{ selectedReviewIds.length }} 条。无效词头需要修正源文件后重新创建批次。</span></div><div><button :disabled="!selectedReviewIds.length" @click="review('approve')">通过</button><button class="danger-button" :disabled="!selectedReviewIds.length" @click="review('reject')">拒绝</button></div></div>
      <div class="table-wrap items"><table><thead><tr><th></th><th>行</th><th>词头</th><th>决策</th><th>审核</th><th>风险 / 错误</th></tr></thead><tbody><tr v-for="item in currentItems" :key="item.itemId"><td><input v-if="item.decision==='review'" v-model="selectedReviewIds" type="checkbox" :value="item.itemId" /></td><td>{{ item.rowNo }}</td><td><strong>{{ item.wordTerm || '—' }}</strong><small v-if="item.normalizedWord">{{ item.normalizedWord }}</small></td><td><span :class="['pill',item.decision==='create'||item.decision==='reuse'?'ok':item.decision==='review'?'warn':'danger']">{{ choiceText(item.decision) }}</span></td><td>{{ item.reviewStatus }}</td><td>{{ item.errorMessage || (item.riskFlags||[]).join('、') || '—' }}</td></tr><tr v-if="!currentItems.length"><td colspan="6" class="empty">该批次尚未开始解析</td></tr></tbody></table></div>
      <p class="hint">发布按钮只会在已核验数据源、无待审项且所有语音任务成功时开放；当前实现会保留失败任务，避免重复调用已成功的 object_key。</p>
    </div></div></div>
  </section>

  <div v-if="previewBook" class="modal-mask" @click.self="previewBook=null"><div class="dialog online-dialog">
    <h2>{{ previewBook.bookName }} · 词条快照</h2>
    <p>保留全部来源义项与例句；来源音标未核实，不冒充已审核 IPA。多义词例句未映射时禁止直接生成和发布。</p>
    <form class="heading-actions" @submit.prevent="openOnlineWords(previewBook,1)"><input v-model="wordKeyword" placeholder="搜索单词" maxlength="80" /><button :disabled="onlineLoading">搜索</button></form>
    <p v-if="onlineLoading">加载中…</p>
    <article v-for="word in onlineWords.items" :key="word.id" class="online-word">
      <strong>{{ word.word_term }}</strong><p>英 {{ word.phonetic_uk || '待补充' }} · 美 {{ word.phonetic_us || '待补充' }}</p>
      <p v-for="(sense,i) in wordData(word).senses" :key="i">{{ i+1 }}. {{ sense.partOfSpeech }} {{ sense.meaning }}</p>
      <div v-for="(ex,i) in wordData(word).sourceExamples" :key="i"><p>{{ ex.sentence }}</p><small>{{ ex.translation }}</small></div>
      <p class="voice-hint">{{ (wordData(word).qualityFlags || []).join(' / ') }}</p>
    </article>
    <div class="form-actions"><span>共 {{ onlineWords.total }} 行 · 第 {{ onlineWords.page }} 页</span><button :disabled="onlineLoading || onlineWords.page<=1" @click="openOnlineWords(previewBook,onlineWords.page-1)">上一页</button><button :disabled="onlineLoading || onlineWords.page*20>=onlineWords.total" @click="openOnlineWords(previewBook,onlineWords.page+1)">下一页</button><button @click="previewBook=null">关闭</button></div>
  </div></div>
  <div v-if="showDataset" class="modal-mask" @click.self="showDataset=false"><form class="dialog" @submit.prevent="saveDataset"><h2>录入词库数据源</h2><p>支持 JSON 数组或以 <code>word</code> 为列名的 CSV。请仅录入已获授权的数据。</p><label>数据集名称<input v-model="datasetForm.datasetName" maxlength="160" required /></label><label>提供方<input v-model="datasetForm.providerName" maxlength="160" required /></label><label>许可状态<select v-model="datasetForm.licenseStatus"><option value="verified">已核验</option><option value="metadata_only">仅元数据</option><option value="unknown">未知</option><option value="restricted">受限</option></select></label><label>授权说明<input v-model="datasetForm.licenseNote" maxlength="1000" required /></label><label>CSV 或 JSON 内容<textarea v-model="datasetForm.payload" required></textarea></label><div class="form-actions"><button type="button" @click="showDataset=false">取消</button><button class="primary" type="submit">保存数据源</button></div></form></div>
  <div v-if="showBatch" class="modal-mask" @click.self="closeBatch"><form class="dialog compact" @submit.prevent="createBatch"><h2>新建词库批次</h2><label>数据源<select v-model="batchForm.datasetId" required><option disabled value="">请选择数据源（不会自动下载目标词书）</option><option v-for="source in datasets" :key="source.datasetId" :value="source.datasetId">{{ source.datasetName }} · {{ source.itemCount }} 词 · {{ licenseText(source.licenseStatus) }}</option></select></label><p v-if="selectedSource" class="voice-hint">本批次来源 {{ selectedSource.itemCount }} 行，不代表整本目标词书总量。来源版本：{{ selectedSource.versionLabel }}。</p><label>目标词书<select v-model="batchForm.targetBookId" required><option disabled value="">请选择目标词书</option><option v-for="book in books" :key="book.bookId" :value="book.bookId">{{ book.bookName }}</option></select></label><div class="two"><label>美式音色<div class="voice-control"><select v-model="batchForm.usVoice"><option v-for="voice in usVoices" :key="voice.code" :value="voice.code">{{ voice.label }}</option></select><button type="button" :disabled="previewingVoice===batchForm.usVoice" @click="previewVoice(batchForm.usVoice)">{{ previewingVoice===batchForm.usVoice?'试听中…':'试听' }}</button></div></label><label>英式音色<div class="voice-control"><select v-model="batchForm.ukVoice"><option v-for="voice in ukVoices" :key="voice.code" :value="voice.code">{{ voice.label }}</option></select><button type="button" :disabled="previewingVoice===batchForm.ukVoice" @click="previewVoice(batchForm.ukVoice)">{{ previewingVoice===batchForm.ukVoice?'试听中…':'试听' }}</button></div></label></div><p class="voice-hint">音色来自阿里云 NLS 英语预设列表；试听只生成临时音频，不写入媒体数据。</p><label>采样率<select v-model.number="batchForm.sampleRate"><option :value="16000">16000 Hz</option><option :value="8000">8000 Hz</option></select></label><label class="check"><input v-model="batchForm.exportSql" type="checkbox" />导出 SQL 审计产物</label><label class="check"><input v-model="batchForm.directPublish" type="checkbox" />通过门禁后直接发布</label><div class="form-actions"><button type="button" @click="closeBatch">取消</button><button class="primary" type="submit">创建批次</button></div></form></div>
</template>

<style scoped>
.heading-actions,.detail-actions,.form-actions,.review-bar>div:last-child{display:flex;gap:9px;align-items:center}.primary,.refresh,.detail-actions button,.form-actions button,.review-bar button{height:39px;padding:0 15px;border:1px solid #c7d6ea;border-radius:8px;color:#43516b;background:#fff}.primary{border-color:#0d6ff5;background:#0d6ff5;color:#fff}.refresh{color:#126ff4}.notice{margin:18px 0;padding:12px 15px;border:1px solid #efc7cb;border-radius:9px;color:#b83c48;background:#fff4f5}.notice.success{border-color:#bfe7d4;color:#087951;background:#effbf5}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-top:24px}.metric{padding:18px 20px}.metric span,.metric strong,.metric small{display:block}.metric span,.metric small{color:#74819a;font-size:12px}.metric strong{margin:7px 0;color:#12214a;font-size:26px}.source-panel,.batch-panel,.detail-panel{margin-top:18px;padding:18px}.panel-title{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.panel-title h2,.detail-head h2{margin:0;color:#14234a;font-size:17px}.panel-title p,.detail-head p,.hint{margin:6px 0 0;color:#75819a;font-size:12px}.table-wrap{overflow:auto;border:1px solid #e2e7ef;border-radius:9px}table{width:100%;border-collapse:collapse;font-size:12px;white-space:nowrap}th{height:40px;padding:0 12px;background:#f8faff;color:#68758e;text-align:left}td{height:47px;padding:0 12px;border-top:1px solid #e9edf3;color:#52617c}td strong,td small{display:block}td strong{color:#1a294c}td small{margin-top:3px;color:#8c97aa;font-size:10px}.empty{text-align:center;color:#8994a7}.pill{display:inline-block;padding:4px 8px;border-radius:99px;color:#9b691e;background:#fff5e6}.pill.ok{color:#078259;background:#e7f8f1}.pill.warn{color:#a56818;background:#fff5e6}.pill.danger{color:#bd3b46;background:#fff0f1}.link{border:0;color:#0d6ff5;background:transparent}.detail-head{display:flex;justify-content:space-between;gap:18px}.eyebrow{color:#0d6ff5;font-size:11px}.stats{display:grid;grid-template-columns:repeat(5,1fr);gap:1px;margin:18px -18px;padding:1px;background:#edf1f6}.stats span{padding:14px 18px;color:#7a879d;background:#fff;font-size:12px}.stats b{display:block;margin-bottom:5px;color:#183160;font-size:19px}.review-bar{display:flex;align-items:center;justify-content:space-between;margin:16px 0;padding:13px 15px;border-radius:9px;background:#fff8e9}.review-bar strong,.review-bar span{display:block}.review-bar span{margin-top:3px;color:#7f7155;font-size:12px}.danger-button{color:#bd3b46!important;border-color:#efc8cf!important}.items{max-height:390px}.hint{margin-top:14px}.modal-mask{position:fixed;inset:0;z-index:10;display:grid;place-items:center;background:rgba(11,25,54,.44)}.dialog{width:min(620px,90vw);max-height:88vh;overflow:auto;padding:25px;border-radius:14px;background:#fff;box-shadow:0 22px 70px rgba(15,35,75,.28)}.dialog h2{margin:0;color:#13234b}.dialog>p{color:#6d7b93;font-size:12px;line-height:1.6}.dialog label{display:grid;gap:6px;margin-top:13px;color:#52617c;font-size:12px}.dialog input,.dialog select,.dialog textarea{box-sizing:border-box;width:100%;min-height:38px;padding:8px 10px;border:1px solid #d5deeb;border-radius:8px;color:#1e315a;background:#fff}.dialog textarea{min-height:160px;resize:vertical;font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}.dialog .two{display:grid;grid-template-columns:1fr 1fr;gap:12px}.dialog .check{display:flex;align-items:center;gap:8px}.dialog .check input{width:auto;min-height:auto}.form-actions{justify-content:flex-end;margin-top:22px}@media(max-width:1000px){.metric-grid{grid-template-columns:repeat(2,1fr)}.stats{grid-template-columns:repeat(3,1fr)}.detail-head{display:block}.detail-actions{margin-top:12px}}@media(max-width:650px){.metric-grid{grid-template-columns:1fr}.heading-actions{flex-wrap:wrap}.stats{grid-template-columns:1fr 1fr}.review-bar{display:block}.review-bar>div:last-child{margin-top:10px}.dialog .two{grid-template-columns:1fr}}
.voice-control{display:flex;gap:7px}.voice-control select{min-width:0}.voice-control button{min-width:58px;border:1px solid #b9d3fb;border-radius:8px;color:#0d6ff5;background:#fff}.voice-control button:disabled{opacity:.6}.voice-hint{margin:11px 0 0;color:#74819a;font-size:11px}
.online-dialog{width:min(900px,94vw)}.batch-detail-dialog{width:min(1420px,94vw);padding:24px}.batch-detail-dialog .detail-panel{margin:0;padding:0}.batch-detail-dialog .stats{margin:18px 0}.online-word{padding:16px 0;border-bottom:1px solid #e2e7ef}.online-word p{white-space:normal;margin:6px 0;line-height:1.6}.online-word strong{font-size:18px;color:#14234a}.online-word small{color:#75819a}.source-panel td small{max-width:310px;white-space:normal}.source-panel button:disabled{opacity:.5}
.catalog-pagination{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-top:12px;color:#74819a;font-size:12px}.catalog-pagination>div,.catalog-pagination label{display:flex;align-items:center;gap:8px}.catalog-pagination select{height:32px;padding:0 26px 0 9px;border:1px solid #d5deeb;border-radius:7px;color:#344768;background:#fff}.catalog-pagination button{height:32px;padding:0 12px;border:1px solid #c7d6ea;border-radius:7px;color:#126ff4;background:#fff}.catalog-pagination button:disabled{cursor:not-allowed;opacity:.45}.delete-link{color:#bd3b46}.delete-link:disabled{opacity:.5;cursor:not-allowed}@media(max-width:650px){.catalog-pagination{align-items:flex-start;flex-direction:column}.catalog-pagination>div{flex-wrap:wrap}}
.metric-link{border:0;text-align:left;cursor:pointer;font:inherit}.metric-link:hover{transform:translateY(-1px);box-shadow:0 8px 22px rgba(25,73,145,.12)}.metric-link:focus-visible{outline:3px solid rgba(13,111,245,.25);outline-offset:2px}.metric-link small{color:#126ff4}.data-sources-section{scroll-margin-top:18px}
</style>
