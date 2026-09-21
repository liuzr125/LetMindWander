<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../../services/request.js'
import { showError } from '../../services/notification.js'

const loading = ref(true), running = ref(false), articleRunning = ref(false), articleCountSaving = ref(false), articleStateSaving = ref(false), detailLoading = ref(false), error = ref('')
const schedule = ref(null), runs = ref([]), pricing = ref(null), articleTask = ref(null), articleRuns = ref([])
const articleCount = ref(5)
const runDetail = ref(null)
const zoneFormatter = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short', hour12: false })
const lastRun = computed(() => runs.value[0] || null)
const mergedRuns = computed(() => [
  ...runs.value.map(item => ({ ...item, kind:'collection', taskName:item.taskName || schedule.value?.name || '每日官方技术资料采集', taskSummary:'官方 RSS / Atom 采集', handled:item.fetchedCount, succeeded:item.insertedCount, skipped:item.skippedCount, failed:'—' })),
  ...articleRuns.value.map(item => ({ ...item, kind:'article_generation', taskName:'每日分词书英语短文', taskSummary:'按当前词书分级原创生成', handled:`${item.bookCount} 本`, succeeded:item.generatedCount, skipped:item.skippedCount, failed:item.failedCount }))
].sort((a,b) => new Date(b.startedAt || b.createdAt || 0) - new Date(a.startedAt || a.createdAt || 0)))
function formatTime(value) { return value ? zoneFormatter.format(new Date(value)) : '尚未运行' }
function stateText(value) { return ({ success: '成功', partial: '部分完成', failed: '失败', running: '运行中' })[value] || value }
function pricingStateText(value) { return ({ success: '同步正常', failed: '同步失败，沿用上一版本', not_run: '等待首次同步' })[value] || '等待首次同步' }
async function load() {
  loading.value = true; error.value = ''
  try {
    const [task, history, priceState, generatedTask, generatedHistory] = await Promise.all([
      request('/admin/collection/schedule'), request('/admin/collection/runs?limit=12'), request('/admin/ai/pricing/status'),
      request('/admin/collection/article-generation/status'), request('/admin/collection/article-generation/runs?limit=12')
    ])
    schedule.value = task; runs.value = history; pricing.value = priceState; articleTask.value = generatedTask; articleRuns.value = generatedHistory; articleCount.value = generatedTask.perBookCount
  }
  catch (e) { error.value = e.message || '任务信息加载失败' }
  finally { loading.value = false }
}
async function runNow() {
  running.value = true; error.value = ''
  try { await request('/admin/collection/run', { method: 'POST' }); await load() }
  catch (e) { error.value = e.message || '采集任务执行失败' }
  finally { running.value = false }
}
async function runArticlesNow() {
  if (!window.confirm(`将按今日缺口调用已配置的 AI 模型，为每本符合条件的词书补齐 ${articleTask.value?.perBookCount || 5} 篇原创短文，可能产生模型费用。是否继续？`)) return
  articleRunning.value = true; error.value = ''
  try { await request('/admin/collection/article-generation/run', { method: 'POST' }); await load() }
  catch (e) { error.value = e.message || '英语短文生成任务执行失败' }
  finally { articleRunning.value = false }
}
async function saveArticleCount() {
  const value = Number(articleCount.value)
  if (!Number.isInteger(value) || value < 1 || value > 20) { showError({ message: '每本词书每天的短文数量必须是 1 至 20 的整数' }); return }
  if (value > (articleTask.value?.perBookCount || 5) && !window.confirm(`数量将从 ${articleTask.value?.perBookCount || 5} 增加到 ${value}，每天模型调用费用和新增数据量会同步增加。是否保存？`)) return
  articleCountSaving.value = true; error.value = ''
  try { articleTask.value = await request('/admin/collection/article-generation/settings', { method: 'PUT', body: JSON.stringify({ perBookCount: value }) }); articleCount.value = articleTask.value.perBookCount }
  catch (_) { /* request 已统一展示提示框 */ }
  finally { articleCountSaving.value = false }
}
async function toggleArticleTask() {
  const enabled = !articleTask.value?.enabled
  const prompt = enabled ? '确定重新启用每日分词书英语短文定时任务吗？' : '确定禁用并停止每日分词书英语短文定时任务吗？禁用后不会在 22:30 调用模型，已生成内容不会删除。'
  if (!window.confirm(prompt)) return
  articleStateSaving.value = true; error.value = ''
  try { articleTask.value = await request('/admin/collection/article-generation/state', { method:'PUT', body:JSON.stringify({ enabled }) }); articleCount.value = articleTask.value.perBookCount }
  catch (_) { /* request 已统一展示提示框 */ }
  finally { articleStateSaving.value = false }
}
async function showDetail(run) {
  detailLoading.value = true; error.value = ''
  const path = run.kind === 'article_generation' ? `/admin/collection/article-generation/runs/${encodeURIComponent(run.id)}/detail` : `/admin/collection/runs/${encodeURIComponent(run.id)}/detail`
  try { runDetail.value = await request(path) }
  catch (e) { error.value = e.message || '读取运行详情失败' }
  finally { detailLoading.value = false }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>任务与运行</h1><p>22:00 采集官方选题，22:30 按用户词书阶段生成原创分级短文。</p></div><div class="actions"><button class="refresh" :disabled="loading || running || articleRunning" @click="load">刷新</button><button class="primary" :disabled="loading || running || articleRunning || !schedule" @click="runNow">{{ running ? '正在采集…' : '立即采集' }}</button></div></div>
    <div v-if="loading" class="panel state">正在加载任务配置…</div>
    <div v-else-if="error && !schedule" class="panel state error">{{ error }}<small>请先导入 V3.3_content_collection_schedule.sql。</small></div>
    <template v-else>
      <div v-if="error" class="warning">{{ error }}</div>
      <div class="metrics"><div class="panel metric"><span>执行计划</span><strong>每天 22:00</strong><small>{{ schedule.timezone }}</small></div><div class="panel metric"><span>每次采集上限</span><strong>{{ schedule.perRunLimit }} 条/来源</strong><small>仅 HTTPS 官方白名单</small></div><div class="panel metric"><span>最近一次</span><strong>{{ lastRun ? stateText(lastRun.state) : '尚未运行' }}</strong><small>{{ lastRun ? formatTime(lastRun.finishedAt || lastRun.startedAt) : '可点击“立即采集”验证' }}</small></div></div>
      <div class="panel task-card"><div><h2>{{ schedule.name }}</h2><p><code>{{ schedule.cronExpression }}</code> · {{ schedule.timezone }} · 状态：<b :class="schedule.state === 'active' ? 'active' : 'paused'">{{ schedule.state === 'active' ? '已启用' : '已暂停' }}</b></p><p class="next">下次运行：{{ formatTime(schedule.nextRunAt) }}</p></div><div class="source-note"><b>采集来源</b><span>Kubernetes · Docker · OpenAI · Spring</span><small>只写入标题、受限摘要、链接与来源许可；新内容默认为“待审核”，不会自动发布。</small></div></div>
      <div v-if="articleTask" class="panel task-card article-task"><div><h2>每日分词书英语短文</h2><p><code>{{ articleTask.cronExpression }}</code> · {{ articleTask.timezone }} · 状态：<b :class="articleTask.enabled ? 'active' : 'paused'">{{ articleTask.enabled ? '已启用' : '已禁用' }}</b></p><p class="next">下次运行：{{ articleTask.enabled ? formatTime(articleTask.nextRunAt) : '定时任务已停止' }}</p><div class="task-actions"><button class="secondary-action" :disabled="articleRunning || !articleTask.enabled" @click="runArticlesNow">{{ articleRunning ? '正在生成…' : '补齐今日短文' }}</button><button :class="['secondary-action',articleTask.enabled?'danger-action':'enable-action']" :disabled="articleStateSaving || articleRunning" @click="toggleArticleTask">{{ articleStateSaving ? '保存中…' : articleTask.enabled ? '禁用定时任务' : '启用定时任务' }}</button></div></div><div class="source-note"><div class="count-setting"><b>{{ articleTask.eligibleBookCount }} 本有效词书 × 每本</b><input v-model.number="articleCount" type="number" min="1" max="20" step="1" aria-label="每本词书每天短文数" /><b>篇</b><button :disabled="articleCountSaving || articleCount === articleTask.perBookCount" @click="saveArticleCount">{{ articleCountSaving ? '保存中…' : '保存' }}</button></div><span>当前可用官方选题 {{ articleTask.availableTopicCount }} 条</span><small>数量范围 1–20；禁用后定时任务不会调用模型，已生成短文和运行记录仍保留。</small></div></div>
      <div v-if="pricing" class="panel task-card pricing-task"><div><h2>AI 价格同步</h2><p><code>{{ pricing.schedule || '每天 22:00' }}</code> · {{ pricing.timezone || 'Asia/Shanghai' }} · 状态：<b :class="pricing.lastStatus === 'success' ? 'active' : 'paused'">{{ pricingStateText(pricing.lastStatus) }}</b></p><p class="next">最近检查：{{ formatTime(pricing.lastCheckedAt) }}</p></div><div class="source-note"><b>DeepSeek 官网价格</b><span>自动核验并冻结有效价格版本</span><small>价格字段不完整时会拒绝更新并继续沿用上一有效版本；模型与费用页保留价格明细和调用账本。</small></div></div>
      <div class="panel table-panel"><div class="table-title"><div><h2>运行历史</h2><p>官方资料采集与英语短文生成按执行时间统一展示；点击详情可查看各自的调度、数据表、步骤和保护措施。</p></div></div><div v-if="!mergedRuns.length" class="empty">暂无运行记录</div><table v-else><thead><tr><th>执行任务</th><th>触发方式</th><th>状态</th><th>处理量</th><th>成功</th><th>跳过</th><th>失败</th><th>完成时间</th><th>说明</th><th>详情</th></tr></thead><tbody><tr v-for="run in mergedRuns" :key="`${run.kind}:${run.id}`"><td><strong>{{ run.taskName }}</strong><small>{{ run.taskSummary }}</small></td><td>{{ run.triggerKey.startsWith('manual:') ? '手动' : '定时' }}</td><td><span :class="['tag', run.state]">{{ stateText(run.state) }}</span></td><td>{{ run.handled }}</td><td>{{ run.succeeded }}</td><td>{{ run.skipped }}</td><td>{{ run.failed }}</td><td>{{ formatTime(run.finishedAt || run.startedAt) }}</td><td class="message">{{ run.errorMessage || '—' }}</td><td><button class="detail-button" :disabled="detailLoading" @click="showDetail(run)">{{ detailLoading ? '读取中…' : '查看详情' }}</button></td></tr></tbody></table></div>
    </template>
  </section>
  <div v-if="runDetail" class="modal-mask" @click.self="runDetail=null">
    <section class="run-modal" role="dialog" aria-modal="true" aria-labelledby="run-detail-title">
      <header class="modal-head"><div><h2 id="run-detail-title">{{ runDetail.kind === 'article_generation' ? '每日分词书英语短文' : runDetail.run.taskName }} · 本次执行详情</h2><p>{{ formatTime(runDetail.run.finishedAt || runDetail.run.startedAt) }} · {{ runDetail.run.triggerKey.startsWith('manual:') ? '手动触发' : '定时触发' }}</p></div><button type="button" aria-label="关闭" @click="runDetail=null">×</button></header>
      <div class="detail-metrics"><div><span>本次状态</span><b :class="['tag',runDetail.run.state]">{{ stateText(runDetail.run.state) }}</b></div><div v-if="runDetail.kind === 'article_generation'"><span>词书 / 生成 / 已存在 / 失败</span><b>{{ runDetail.run.bookCount }} / {{ runDetail.run.generatedCount }} / {{ runDetail.run.skippedCount }} / {{ runDetail.run.failedCount }}</b></div><div v-else><span>采集 / 新入库 / 去重</span><b>{{ runDetail.run.fetchedCount }} / {{ runDetail.run.insertedCount }} / {{ runDetail.run.skippedCount }}</b></div><div><span>任务代码</span><code>{{ runDetail.taskCode }}</code></div></div>
      <section v-if="runDetail.kind === 'article_generation'" class="detail-section"><h3>本次使用的 AI 模型</h3><ul v-if="runDetail.models?.length"><li v-for="model in runDetail.models" :key="`${model.providerCode}:${model.modelCode}`"><code>{{ model.providerCode }} / {{ model.modelCode }}</code> · {{ model.callCount }} 次调用</li></ul><p v-else>本次没有可关联的模型调用记录（可能在调用模型前失败，或是旧版本生成的运行记录）。</p></section>
      <section class="detail-section"><h3>调度与 SQL 脚本</h3><p><code>{{ runDetail.sqlScript }}</code></p><p v-if="runDetail.kind === 'article_generation'">Cron：<code>{{ runDetail.status.cronExpression }}</code> · {{ runDetail.status.timezone }} · 每本目标 {{ runDetail.status.perBookCount }} 篇 · 当前{{ runDetail.status.enabled ? '启用' : '禁用' }}</p><p v-else>Cron：<code>{{ runDetail.schedule.cronExpression }}</code> · {{ runDetail.schedule.timezone }} · 每来源上限 {{ runDetail.schedule.perRunLimit }} 条</p><h4>涉及数据表</h4><ul><li v-for="item in runDetail.tables" :key="item">{{ item }}</li></ul><template v-if="runDetail.sqlOperations"><h4>本次任务使用的 SQL 操作</h4><ul><li v-for="item in runDetail.sqlOperations" :key="item">{{ item }}</li></ul></template></section>
      <section class="detail-section grid"><div><h3>使用的工具 / 组件</h3><ul><li v-for="item in runDetail.tools" :key="item">{{ item }}</li></ul></div><div><h3>执行步骤</h3><ol><li v-for="item in runDetail.steps" :key="item">{{ item }}</li></ol></div></section>
      <section class="detail-section"><h3>执行原理与保护措施</h3><ul><li v-for="item in runDetail.principles" :key="item">{{ item }}</li></ul></section>
      <footer v-if="runDetail.run.errorCode || runDetail.run.errorMessage" class="detail-error">错误：{{ runDetail.run.errorCode ? `${runDetail.run.errorCode} · ` : '' }}{{ runDetail.run.errorMessage || '无额外说明' }}</footer>
    </section>
  </div>
</template>

<style scoped>
.actions{display:flex;gap:10px}.refresh,.primary{height:38px;padding:0 17px;border-radius:9px;font-weight:650}.refresh{border:1px solid #cfdcf0;color:#126ff4;background:#fff}.primary{border:0;color:#fff;background:#126ff4}.primary:disabled,.refresh:disabled{cursor:not-allowed;opacity:.55}.state{margin-top:24px;padding:54px;color:#77849a;text-align:center}.state small{display:block;margin-top:8px}.error{color:#c53f4c}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:24px}.metric{padding:20px 22px}.metric span,.metric small{display:block;color:#71809a;font-size:13px}.metric strong{display:block;margin:10px 0 6px;font-size:23px}.task-card{display:flex;justify-content:space-between;gap:30px;margin-top:20px;padding:22px}.task-card h2,.table-panel h2{margin:0;font-size:18px}.task-card p{margin:9px 0 0;color:#687692;font-size:13px}.task-card code{padding:3px 6px;border-radius:5px;background:#f1f5fb;color:#30517b}.active{color:#17835d}.paused{color:#b26a16}.next{font-weight:600}.source-note{display:flex;max-width:480px;flex-direction:column;gap:6px;padding:13px 15px;border-radius:9px;background:#f7faff;color:#52617e;font-size:13px}.source-note small{color:#79869c;line-height:1.55}.table-panel{margin-top:20px;padding:22px}.table-title p{margin:6px 0 16px;color:#78849a;font-size:13px}.empty{padding:45px;color:#77849a;text-align:center}table{width:100%;border-collapse:collapse}th,td{padding:13px 10px;border-bottom:1px solid #edf0f5;text-align:left;font-size:13px}th{color:#75819a;font-size:12px;font-weight:650}td strong{display:block;color:#273a5b;font-size:12px}td small{display:block;margin-top:4px;color:#8390a5;font-size:11px}.tag{display:inline-block;padding:4px 9px;border-radius:999px;font-size:12px}.success{color:#15845f;background:#e8f8f1}.partial{color:#b2700d;background:#fff4df}.failed{color:#c43a47;background:#fff0f1}.running{color:#156fc7;background:#e8f3ff}.message{max-width:190px;color:#75819a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.detail-button{border:0;color:#126ff4;background:transparent;font-size:12px;font-weight:700;white-space:nowrap}.detail-button:disabled{opacity:.5}.warning{margin-top:18px;padding:12px 15px;border:1px solid #f2d59e;border-radius:10px;color:#955e12;background:#fff9ed}.run-modal{box-sizing:border-box;width:min(1000px,calc(100vw - 48px));max-height:90vh;overflow:auto;padding:28px;border-radius:16px;background:#fff;box-shadow:0 24px 80px rgba(15,31,63,.25)}.modal-head{display:flex;justify-content:space-between;gap:18px}.modal-head h2{margin:0;color:#162946;font-size:21px}.modal-head p{margin:8px 0 0;color:#74819a;font-size:13px}.modal-head>button{width:34px;height:34px;border:0;border-radius:7px;color:#56657c;background:#f0f4f9;font-size:24px}.detail-metrics{display:grid;grid-template-columns:1fr 1.4fr 1.7fr;gap:12px;margin:22px 0}.detail-metrics>div{display:grid;gap:7px;padding:13px;border-radius:9px;background:#f6f9fd}.detail-metrics span{color:#77859c;font-size:12px}.detail-metrics b,.detail-metrics code{color:#263959;font-size:13px;overflow-wrap:anywhere}.detail-section{margin-top:16px;padding:17px 18px;border:1px solid #e0e8f3;border-radius:10px}.detail-section h3{margin:0;color:#233956;font-size:15px}.detail-section h4{margin:16px 0 0;color:#52647f;font-size:12px}.detail-section p{margin:10px 0 0;color:#5c6b83;font-size:13px;line-height:1.7}.detail-section code{padding:3px 5px;border-radius:4px;color:#375e94;background:#eef4fd;font-size:12px;overflow-wrap:anywhere}.detail-section ul,.detail-section ol{margin:10px 0 0;padding-left:20px;color:#53627a;font-size:13px;line-height:1.75}.detail-section.grid{display:grid;grid-template-columns:1fr 1fr;gap:22px}.detail-section.grid>div+div{border-left:1px solid #e3eaf4;padding-left:22px}.detail-error{margin-top:16px;padding:12px;border-radius:8px;color:#a33b42;background:#fff0f1;font-size:12px}@media(max-width:900px){.metrics,.detail-metrics,.detail-section.grid{grid-template-columns:1fr}.task-card{display:block}.source-note{max-width:none;margin-top:18px}.detail-section.grid>div+div{border-left:0;border-top:1px solid #e3eaf4;margin-top:14px;padding:14px 0 0}table{min-width:1000px}.table-panel{overflow:auto}}
.secondary-action{margin-top:15px;padding:8px 13px;border:1px solid #bad1f6;border-radius:8px;color:#126ff4;background:#fff;font-weight:650}.secondary-action:disabled{cursor:not-allowed;opacity:.55}.article-task{border-color:#cfe0fb}.table-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.count-setting{display:flex;align-items:center;gap:8px}.count-setting input{box-sizing:border-box;width:68px;height:34px;padding:0 8px;border:1px solid #cbd9ed;border-radius:7px;color:#243858;background:#fff}.count-setting button{height:34px;padding:0 12px;border:1px solid #b8cef0;border-radius:7px;color:#126ff4;background:#fff;font-weight:650}.count-setting button:disabled{cursor:not-allowed;opacity:.5}
.task-actions{display:flex;align-items:center;gap:10px}.danger-action{border-color:#efb7bd;color:#bd3d48}.enable-action{border-color:#a9dec8;color:#14815d}
</style>
