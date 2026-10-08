<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { request } from '../../services/request.js'
import { showError } from '../../services/notification.js'

const loading = ref(true), error = ref('')
const runningTasks = ref({}), savingStateTasks = ref({}), detailLoading = ref(false), resultMessage = ref('')
const schedule = ref(null), runs = ref([]), articleTask = ref(null), articleRuns = ref([]), pricing = ref(null), pricingRuns = ref([])
const taskDetail = ref(null), runDetail = ref(null), articleCount = ref(5), articleCountSaving = ref(false)
const editingPlan = ref(null), planMode = ref('daily'), planTime = ref('22:00'), planCron = ref(''), planSaving = ref(false), planError = ref(''), planInput = ref(null)
const zoneFormatter = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short', hour12: false })

function formatTime(value) { return value ? zoneFormatter.format(new Date(value)) : '—' }
function stateText(value) { return ({ success: '成功', partial: '部分完成', failed: '失败', running: '运行中', active: '已启用', paused: '已停止' })[value] || value || '—' }
function triggerText(value) { return String(value || '').startsWith('manual:') ? '手动执行' : '定时执行' }
function pricingStateText(value) { return ({ success: '同步正常', failed: '同步失败', not_run: '尚未运行' })[value] || '尚未运行' }
function pricingRunValue(item, key) { return item?.[key] ?? item?.[key.replace(/_([a-z])/g, (_, c) => c.toUpperCase())] }
const runFailure = computed(() => {
  const run = runDetail.value?.run
  if (!run) return null
  const message = String(pricingRunValue(run, 'error_message') || '').trim()
  const code = String(pricingRunValue(run, 'error_code') || '').trim()
  const failed = run.state === 'failed'
  const partial = run.state === 'partial' || Number(run.failedCount || 0) > 0
  if (!message && !code && !failed && !partial) return null
  return { title: failed ? '失败原因' : partial ? '未完成原因' : '执行说明', message: message || '本次运行记录未保存具体原因，请检查服务日志。', code }
})
const tasks = computed(() => {
  const items = []
  if (schedule.value) items.push({ code: 'collection', name: schedule.value.name || '每日官方技术资料采集', schedule: schedule.value.cronExpression, timezone: schedule.value.timezone, enabled: schedule.value.state === 'active', nextRunAt: schedule.value.nextRunAt, lastRun: runs.value[0], description: '从 Kubernetes、Docker、OpenAI、Spring 等官方 RSS/Atom 白名单收集候选技术资料；新内容默认待审核。' })
  if (articleTask.value) items.push({ code: 'article', name: '每日分词书英语短文', schedule: articleTask.value.cronExpression, timezone: articleTask.value.timezone, enabled: articleTask.value.enabled, nextRunAt: articleTask.value.nextRunAt, lastRun: articleRuns.value[0], description: '根据已启用词书与官方选题生成原创分级英语短文；运行前经过 AI 配额、预算和内容校验。' })
  if (pricing.value) items.push({ code: 'pricing', name: 'AI 价格同步', schedule: pricing.value.cronExpression || pricing.value.schedule || '0 0 22 * * *', timezone: pricing.value.timezone || 'Asia/Shanghai', enabled: pricing.value.enabled, nextRunAt: pricing.value.nextRunAt, lastRun: pricingRuns.value[0], description: '核验 DeepSeek 官网公开价格并冻结有效版本；页面异常或字段不完整时保留上一个有效版本。' })
  return items
})
function historyFor(code) {
  if (code === 'collection') return runs.value.map(item => ({ ...item, taskCode: code, handled: item.fetchedCount, succeeded: item.insertedCount, skipped: item.skippedCount, failed: '—' }))
  if (code === 'article') return articleRuns.value.map(item => ({ ...item, taskCode: code, handled: `${item.bookCount || 0} 本词书`, succeeded: item.generatedCount, skipped: item.skippedCount, failed: item.failedCount }))
  return pricingRuns.value.map(item => ({ ...item, taskCode: code, handled: `${pricingRunValue(item, 'changed_model_count') || 0} 个模型更新`, succeeded: item.state === 'success' ? '—' : 0, skipped: '—', failed: item.state === 'failed' ? 1 : '—' }))
}
async function load(background = false) {
  if (!background) loading.value = true
  error.value = ''
  try {
    const [collection, collectionRuns, article, generatedRuns, price, priceHistory] = await Promise.all([
      request('/admin/collection/schedule'), request('/admin/collection/runs?limit=12'), request('/admin/collection/article-generation/status'), request('/admin/collection/article-generation/runs?limit=12'), request('/admin/ai/pricing/status'), request('/admin/collection/pricing/runs?limit=12')
    ])
    schedule.value = collection; runs.value = collectionRuns; articleTask.value = article; articleRuns.value = generatedRuns; pricing.value = price; pricingRuns.value = priceHistory; articleCount.value = article.perBookCount
    if (taskDetail.value) taskDetail.value = tasks.value.find(item => item.code === taskDetail.value.code) || taskDetail.value
  } catch (e) { error.value = e.message || '任务信息加载失败' }
  finally { loading.value = false }
}
async function runTask(task) {
  if (!task.enabled || runningTasks.value[task.code] || savingStateTasks.value[task.code]) return
  if (task.code === 'article' && !window.confirm(`将按今日缺口调用已配置的 AI 模型，为每本符合条件的词书补齐 ${articleTask.value?.perBookCount || 5} 篇原创短文，可能产生模型费用。是否继续？`)) return
  const routes = { collection: '/admin/collection/run', article: '/admin/collection/article-generation/run', pricing: '/admin/collection/pricing/run' }
  runningTasks.value[task.code] = true; error.value = ''; resultMessage.value = ''
  try {
    const result = await request(routes[task.code], { method: 'POST' })
    resultMessage.value = task.name + '：' + stateText(result.state) + (result.errorMessage || result.error_message ? '，' + (result.errorMessage || result.error_message) : '')
    await load(true)
  }
  catch (e) { error.value = e.message || '任务执行失败' }
  finally { delete runningTasks.value[task.code] }
}
async function toggleTask(task) {
  if (savingStateTasks.value[task.code]) return
  const enabled = !task.enabled
  const label = enabled ? '启用' : '停止'
  if (!window.confirm(`确定${label}“${task.name}”吗？${enabled ? '下一个计划周期将恢复执行。' : '已在运行的本次操作不会被中断，后续定时触发将停止。'}`)) return
  const routes = { collection: '/admin/collection/schedule/state', article: '/admin/collection/article-generation/state', pricing: '/admin/collection/pricing/state' }
  savingStateTasks.value[task.code] = true; error.value = ''; resultMessage.value = ''
  try {
    await request(routes[task.code], { method: 'PUT', body: JSON.stringify({ enabled }) })
    resultMessage.value = task.name + '已' + label
    await load(true)
  }
  catch (e) { error.value = e.message || '任务状态保存失败' }
  finally { delete savingStateTasks.value[task.code] }
}
function openTaskDetail(task) { taskDetail.value = task }
async function editPlan(task) {
  editingPlan.value = task; planError.value = ''; planCron.value = task.schedule
  const daily = /^0 (\d{1,2}) (\d{1,2}) \* \* \*$/.exec(task.schedule)
  planMode.value = daily ? 'daily' : 'cron'
  planTime.value = daily ? `${daily[2].padStart(2, '0')}:${daily[1].padStart(2, '0')}` : '22:00'
  await nextTick(); planInput.value?.focus()
}
function closePlan() { if (!planSaving.value) editingPlan.value = null }
async function savePlan() {
  let cron = planCron.value.trim()
  if (planMode.value === 'daily') {
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(planTime.value)) { planError.value = '请选择有效的每日执行时间'; return }
    const [hour, minute] = planTime.value.split(':').map(Number)
    cron = `0 ${minute} ${hour} * * *`
  }
  if (cron.split(/\s+/).length !== 6) { planError.value = '请输入六段 Cron：秒 分 时 日 月 周'; return }
  planSaving.value = true; planError.value = ''
  try {
    await request(`/admin/tasks/${editingPlan.value.code}/schedule`, { method: 'PUT', body: JSON.stringify({ cronExpression: cron }), silentError: true })
    resultMessage.value = `${editingPlan.value.name}的执行计划已保存并生效${editingPlan.value.enabled ? '' : '；任务仍保持停止状态'}`
    editingPlan.value = null; await load(true)
  } catch (e) { planError.value = e.message || '计划保存失败，请重试' }
  finally { planSaving.value = false }
}
async function saveArticleCount() {
  const value = Number(articleCount.value)
  if (!Number.isInteger(value) || value < 1 || value > 20) { showError({ message: '每本词书每天的短文数量必须是 1 至 20 的整数' }); return }
  articleCountSaving.value = true
  try { articleTask.value = await request('/admin/collection/article-generation/settings', { method: 'PUT', body: JSON.stringify({ perBookCount: value }) }); articleCount.value = articleTask.value.perBookCount; taskDetail.value = tasks.value.find(item => item.code === 'article') || taskDetail.value }
  finally { articleCountSaving.value = false }
}
async function showRunDetail(run) {
  if (run.taskCode === 'pricing') { runDetail.value = { kind: 'pricing', run }; return }
  detailLoading.value = true
  try { runDetail.value = await request(run.taskCode === 'article' ? `/admin/collection/article-generation/runs/${encodeURIComponent(run.id)}/detail` : `/admin/collection/runs/${encodeURIComponent(run.id)}/detail`) }
  catch (e) { error.value = e.message || '读取运行详情失败' }
  finally { detailLoading.value = false }
}
onMounted(() => load())
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>任务与运行</h1><p>统一管理定时任务；可查看配置与历史记录，并对单个任务立即执行或停止。</p></div><button class="refresh" :disabled="loading" @click="load()">刷新</button></div>
    <div v-if="loading" class="panel state">正在加载任务配置…</div>
    <div v-else-if="error && !tasks.length" class="panel state error">{{ error }}<small>请先导入对应的数据库迁移脚本。</small></div>
    <template v-else>
      <div v-if="error" class="warning" role="alert">{{ error }}</div>
      <div v-if="resultMessage" class="result-message" role="status">{{ resultMessage }}</div>
      <div class="panel list-panel"><div class="table-title"><div><h2>定时任务列表</h2><p>停止后不再接收后续定时触发；正在执行中的本次任务不会被中断，运行记录会继续保留。</p></div><strong>{{ tasks.length }} 个任务</strong></div>
        <div class="task-table-wrap"><table class="task-table"><thead><tr><th>任务名称</th><th>执行计划</th><th>状态</th><th>下次运行</th><th>最近执行</th><th>操作</th></tr></thead>
          <tbody><tr v-for="task in tasks" :key="task.code">
            <td><strong>{{ task.name }}</strong><small>{{ task.description }}</small></td>
            <td class="schedule-cell"><code>{{ task.schedule }}</code><small>{{ task.timezone }}</small></td>
            <td><span :class="['tag', task.enabled ? 'active' : 'paused']">{{ task.enabled ? '已启用' : '已停止' }}</span></td>
            <td>{{ task.enabled ? (task.nextRunAt ? formatTime(task.nextRunAt) : '按计划执行') : '任务已停止' }}</td>
            <td><template v-if="task.lastRun"><span :class="['tag', task.lastRun.state]">{{ stateText(task.lastRun.state) }}</span><small>{{ formatTime(task.lastRun.finishedAt || task.lastRun.startedAt || pricingRunValue(task.lastRun, 'finished_at') || pricingRunValue(task.lastRun, 'started_at')) }}</small></template><span v-else class="muted">暂无记录</span></td>
            <td><div class="row-actions">
              <button class="link-button" @click="openTaskDetail(task)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 3h8l4 4v14H4V3h4ZM16 3v5h4M8 12h8M8 16h6" /></svg>详情</button>
              <button class="link-button edit-plan" :disabled="planSaving || savingStateTasks[task.code]" :aria-label="`修改${task.name}的执行计划`" @click="editPlan(task)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5 4 4M4 20l4-1L20 7a2.8 2.8 0 0 0-4-4L4 15v5Z" /></svg>修改计划</button>
              <button class="link-button run" :disabled="!task.enabled || runningTasks[task.code] || savingStateTasks[task.code] || planSaving" @click="runTask(task)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 11 7-11 7V5Z" /></svg>{{ runningTasks[task.code] ? '执行中…' : '立即执行' }}</button>
              <button :class="['link-button', task.enabled ? 'stop' : 'enable']" :disabled="savingStateTasks[task.code] || planSaving" @click="toggleTask(task)"><svg viewBox="0 0 24 24" aria-hidden="true"><path v-if="task.enabled" d="M7 5v14M17 5v14" /><path v-else d="m7 5 11 7-11 7V5Z" /></svg>{{ savingStateTasks[task.code] ? '保存中…' : task.enabled ? '停止' : '启用' }}</button>
            </div></td>
          </tr></tbody>
        </table></div>
      </div>
    </template>
  </section>

  <div v-if="editingPlan" class="modal-mask top" @click.self="closePlan" @keydown.esc="closePlan">
    <form class="plan-modal" role="dialog" aria-modal="true" aria-labelledby="plan-title" @submit.prevent="savePlan">
      <header class="modal-head"><div><h2 id="plan-title">修改执行计划</h2><p>{{ editingPlan.name }} · 北京时间</p></div><button type="button" :disabled="planSaving" aria-label="关闭" @click="closePlan">×</button></header>
      <div class="plan-tabs"><button type="button" :class="{ selected: planMode === 'daily' }" :disabled="planSaving" @click="planMode='daily'">每天执行</button><button type="button" :class="{ selected: planMode === 'cron' }" :disabled="planSaving" @click="planMode='cron'">自定义 Cron</button></div>
      <label class="plan-field"><span>{{ planMode === 'daily' ? '每日执行时间' : '六段 Cron 表达式' }}</span><input v-if="planMode === 'daily'" ref="planInput" v-model="planTime" type="time" required :disabled="planSaving" /><input v-else ref="planInput" v-model="planCron" type="text" maxlength="100" placeholder="0 30 22 * * *" required :disabled="planSaving" /><small v-if="planMode === 'cron'">顺序：秒 分 时 日 月 周，例如 0 30 22 * * * 表示每天 22:30。</small></label>
      <p class="plan-help">保存后立即更新后续调度，重启后仍保留。不会立即执行，也不会中断正在运行的任务；已停止的任务保持停止。</p>
      <p v-if="planError" class="plan-error" role="alert">{{ planError }}</p>
      <footer class="plan-footer"><button class="plan-cancel" type="button" :disabled="planSaving" @click="closePlan">取消</button><button class="plan-save" type="submit" :disabled="planSaving">{{ planSaving ? '保存中…' : '保存计划' }}</button></footer>
    </form>
  </div>

  <div v-if="taskDetail" class="modal-mask" @click.self="taskDetail=null"><section class="task-modal" role="dialog" aria-modal="true" aria-labelledby="task-detail-title"><header class="modal-head"><div><h2 id="task-detail-title">{{ taskDetail.name }}</h2><p>{{ taskDetail.description }}</p></div><button type="button" aria-label="关闭" @click="taskDetail=null">×</button></header><div class="detail-metrics"><div><span>执行计划</span><b><code>{{ taskDetail.schedule }}</code></b></div><div><span>时区</span><b>{{ taskDetail.timezone }}</b></div><div><span>当前状态</span><b :class="['tag',taskDetail.enabled ? 'active' : 'paused']">{{ taskDetail.enabled ? '已启用' : '已停止' }}</b></div></div><section v-if="taskDetail.code === 'article'" class="detail-section count-config"><div><h3>生成数量设置</h3><p>每本有效词书每天生成的原创短文数量，范围 1–20；增加数量会提升 AI 调用成本。</p></div><div><input v-model.number="articleCount" type="number" min="1" max="20" aria-label="每本词书每日短文数量" /><span>篇 / 本 / 天</span><button :disabled="articleCountSaving || articleCount === articleTask?.perBookCount" @click="saveArticleCount">{{ articleCountSaving ? '保存中…' : '保存' }}</button></div></section><section class="detail-section"><h3>历史执行记录</h3><div v-if="!historyFor(taskDetail.code).length" class="empty">暂无运行记录</div><div v-else class="history-wrap"><table><thead><tr><th>触发方式</th><th>状态</th><th>处理量</th><th>成功</th><th>跳过</th><th>失败</th><th>完成时间</th><th>操作</th></tr></thead><tbody><tr v-for="run in historyFor(taskDetail.code)" :key="run.id"><td>{{ triggerText(run.triggerKey || pricingRunValue(run, 'trigger_key')) }}</td><td><span :class="['tag',run.state]">{{ stateText(run.state) }}</span></td><td>{{ run.handled }}</td><td>{{ run.succeeded }}</td><td>{{ run.skipped }}</td><td>{{ run.failed }}</td><td>{{ formatTime(run.finishedAt || run.startedAt || pricingRunValue(run, 'finished_at') || pricingRunValue(run, 'started_at')) }}</td><td><button class="link-button" :disabled="detailLoading" @click="showRunDetail(run)">{{ detailLoading ? '读取中…' : '查看详情' }}</button></td></tr></tbody></table></div></section></section></div>

  <div v-if="runDetail" class="modal-mask top" @click.self="runDetail=null">
    <section class="run-modal" role="dialog" aria-modal="true">
      <header class="modal-head"><div><h2>{{ runDetail.kind === 'pricing' ? 'AI 价格同步' : runDetail.kind === 'article_generation' ? '每日分词书英语短文' : runDetail.run.taskName }} · 本次执行详情</h2><p>{{ formatTime(runDetail.run.finishedAt || runDetail.run.startedAt || pricingRunValue(runDetail.run, 'finished_at') || pricingRunValue(runDetail.run, 'started_at')) }} · {{ triggerText(runDetail.run.triggerKey || pricingRunValue(runDetail.run, 'trigger_key')) }}</p></div><button type="button" aria-label="关闭" @click="runDetail=null">×</button></header>
      <div class="detail-metrics">
        <div><span>本次状态</span><b :class="['tag',runDetail.run.state]">{{ stateText(runDetail.run.state) }}</b></div>
        <div v-if="runDetail.kind === 'pricing'"><span>更新模型数</span><b>{{ pricingRunValue(runDetail.run, 'changed_model_count') || 0 }}</b></div>
        <div v-else-if="runDetail.kind === 'article_generation'"><span>词书 / 生成 / 已存在 / 失败</span><b>{{ runDetail.run.bookCount }} / {{ runDetail.run.generatedCount }} / {{ runDetail.run.skippedCount }} / {{ runDetail.run.failedCount }}</b></div>
        <div v-else><span>采集 / 新入库 / 去重</span><b>{{ runDetail.run.fetchedCount }} / {{ runDetail.run.insertedCount }} / {{ runDetail.run.skippedCount }}</b></div>
        <div v-if="runDetail.kind !== 'pricing'"><span>任务代码</span><code>{{ runDetail.taskCode }}</code></div>
      </div>
      <section v-if="runFailure" class="run-failure" aria-labelledby="run-failure-title">
        <h3 id="run-failure-title">{{ runFailure.title }}</h3>
        <p>{{ runFailure.message }}</p>
        <div v-if="runFailure.code" class="failure-code">诊断码：<code>{{ runFailure.code }}</code></div>
      </section>
      <section v-if="runDetail.kind === 'pricing'" class="detail-section"><h3>任务原理</h3><p>请求 DeepSeek 官方定价页面，完整校验模型、峰谷时段和全部费率字段后才写入新的价格版本；任一校验失败时，本次标记失败，旧版价格继续使用。</p></section>
      <template v-else>
        <section class="detail-section"><h3>调度与 SQL 脚本</h3><p><code>{{ runDetail.sqlScript }}</code></p><h4>涉及数据表</h4><ul><li v-for="item in runDetail.tables" :key="item">{{ item }}</li></ul></section>
        <section class="detail-section grid"><div><h3>使用的工具 / 组件</h3><ul><li v-for="item in runDetail.tools" :key="item">{{ item }}</li></ul></div><div><h3>执行步骤</h3><ol><li v-for="item in runDetail.steps" :key="item">{{ item }}</li></ol></div></section>
        <section class="detail-section"><h3>执行原理与保护措施</h3><ul><li v-for="item in runDetail.principles" :key="item">{{ item }}</li></ul></section>
      </template>
    </section>
  </div>
</template>

<style scoped>
.run-failure{margin:0 0 18px;padding:16px 18px;border:1px solid #f0c6cc;border-radius:10px;background:#fff6f7;color:#a73543}
.run-failure h3{margin:0;font-size:14px;font-weight:700}
.run-failure p{margin:10px 0 0;font-size:13px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}
.failure-code{margin-top:10px;font-size:12px}.failure-code code{padding:3px 6px;border-radius:4px;background:#fbe9ec;white-space:pre-wrap;overflow-wrap:anywhere}
.edit-plan svg,.row-actions svg{width:14px;height:14px;flex-shrink:0;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.list-panel .row-actions .link-button{gap:5px;transition:background .15s,box-shadow .15s}
.list-panel .row-actions .link-button:hover:not(:disabled){box-shadow:0 2px 6px rgba(24,64,126,.1)}
.plan-modal{box-sizing:border-box;width:min(490px,calc(100vw - 32px));max-height:90vh;overflow:auto;margin:0;padding:28px;border-radius:16px;background:#fff;box-shadow:0 24px 80px rgba(15,31,63,.25)}
.plan-tabs{display:flex;gap:5px;margin:24px 0 20px;padding:4px;border-radius:9px;background:#f0f4fa}
.plan-tabs button{flex:1;height:34px;border:0;border-radius:6px;background:transparent;color:#65758d;font-size:13px}
.plan-tabs button.selected{background:#fff;color:#126ff4;font-weight:650;box-shadow:0 1px 5px rgba(25,54,102,.1)}
.plan-field{display:grid;gap:9px;color:#334968;font-size:13px;font-weight:600}
.plan-field input{box-sizing:border-box;width:100%;height:44px;padding:0 12px;border:1px solid #cad8eb;border-radius:8px;color:#243858;background:#fff;font-size:15px;outline:none}
.plan-field input:focus{border-color:#126ff4;box-shadow:0 0 0 3px #126ff41a}
.plan-field small{color:#7b879a;font-size:12px;font-weight:400;line-height:1.6}
.plan-help{margin:18px 0 0;padding:12px;border-radius:8px;background:#f5f8fd;color:#70809a;font-size:12px;line-height:1.7}
.plan-error{margin:12px 0 0;color:#bd3d48;font-size:13px}
.plan-footer{display:flex;justify-content:flex-end;gap:10px;height:auto;margin-top:24px;line-height:normal}
.plan-footer button{height:38px;padding:0 17px;border-radius:8px;font-size:13px;font-weight:650}
.plan-cancel{border:1px solid #d3deec;background:#fff;color:#60728c}.plan-save{border:1px solid #126ff4;background:#126ff4;color:#fff}
.plan-modal button:disabled{opacity:.55;cursor:not-allowed}.plan-modal button:focus-visible,.edit-plan:focus-visible{outline:2px solid #126ff4;outline-offset:3px}
.task-table{table-layout:fixed;min-width:1120px}
.task-table th:nth-child(1){width:30%}
.task-table th:nth-child(2){width:15%}
.task-table th:nth-child(3){width:8%}
.task-table th:nth-child(4){width:15%}
.task-table th:nth-child(5){width:13%}
.task-table th:nth-child(6){width:270px}
.task-table td code,.task-table .tag{white-space:nowrap}
.task-table td strong,.task-table td small{max-width:none}
.list-panel .row-actions{display:grid;grid-template-columns:repeat(2,max-content);align-items:center;gap:8px;min-width:0}
.list-panel .row-actions .link-button{appearance:none;display:inline-flex;align-items:center;justify-content:center;min-height:32px;padding:0 10px;margin:0;border:1px solid #cddff9;border-radius:7px;color:#126ff4;background:#f4f8ff;font-family:inherit;font-size:12px;font-weight:600;line-height:1.2;white-space:nowrap;box-shadow:none}
.list-panel .row-actions .link-button.run{background:#126ff4;border-color:#126ff4;color:#fff;min-width:76px}
.list-panel .row-actions .link-button.stop{color:#bd3d48;border-color:#f0c6cc;background:#fff6f7}
.list-panel .row-actions .link-button.enable{color:#14815d;border-color:#bae2d2;background:#effaf5}
.list-panel .row-actions .link-button:hover:not(:disabled){filter:brightness(.96)}
.list-panel .row-actions .link-button:focus-visible{outline:2px solid #126ff4;outline-offset:3px}
.list-panel .row-actions .link-button:disabled{opacity:.55;cursor:wait}
.result-message{margin-top:18px;padding:12px 15px;border:1px solid #bedfd4;border-radius:10px;color:#21745d;background:#effaf5;font-size:13px}

.refresh{height:38px;padding:0 17px;border:1px solid #cfdcf0;border-radius:9px;color:#126ff4;background:#fff;font-weight:650}.refresh:disabled,.link-button:disabled{cursor:not-allowed;opacity:.5}.state{margin-top:24px;padding:54px;color:#77849a;text-align:center}.state small{display:block;margin-top:8px}.error{color:#c53f4c}.warning{margin-top:18px;padding:12px 15px;border:1px solid #f2d59e;border-radius:10px;color:#955e12;background:#fff9ed}.list-panel{margin-top:24px;padding:22px}.table-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.table-title h2{margin:0;font-size:18px}.table-title p{margin:6px 0 16px;color:#78849a;font-size:13px}.table-title>strong{padding:6px 10px;border-radius:999px;color:#126ff4;background:#eff5ff;font-size:12px}.task-table-wrap,.history-wrap{overflow:auto}.task-table{min-width:1030px}table{width:100%;border-collapse:collapse}th,td{padding:13px 10px;border-bottom:1px solid #edf0f5;text-align:left;font-size:13px;vertical-align:middle}th{color:#75819a;font-size:12px;font-weight:650}td strong{display:block;max-width:235px;color:#273a5b;font-size:13px}td small{display:block;max-width:250px;margin-top:5px;color:#8390a5;font-size:11px;line-height:1.45}td code,.detail-section code{padding:3px 5px;border-radius:4px;color:#375e94;background:#eef4fd;font-size:12px}.tag{display:inline-block;padding:4px 9px;border-radius:999px;font-size:12px}.active,.success{color:#15845f;background:#e8f8f1}.paused{color:#9a5d13;background:#fff4df}.partial{color:#b2700d;background:#fff4df}.failed{color:#c43a47;background:#fff0f1}.running{color:#156fc7;background:#e8f3ff}.muted{color:#8995a9}.row-actions{display:flex;flex-wrap:wrap;gap:8px;min-width:220px}.link-button{padding:0;border:0;color:#126ff4;background:transparent;font-size:12px;font-weight:700;white-space:nowrap}.link-button.stop{color:#bd3d48}.link-button.enable{color:#14815d}.modal-mask{position:fixed;z-index:30;inset:0;display:flex;align-items:center;justify-content:center;padding:24px;background:rgba(15,31,63,.42)}.modal-mask.top{z-index:40}.task-modal,.run-modal{box-sizing:border-box;width:min(1050px,calc(100vw - 48px));max-height:90vh;overflow:auto;padding:28px;border-radius:16px;background:#fff;box-shadow:0 24px 80px rgba(15,31,63,.25)}.modal-head{display:flex;justify-content:space-between;gap:18px}.modal-head h2{margin:0;color:#162946;font-size:21px}.modal-head p{margin:8px 0 0;color:#74819a;font-size:13px;line-height:1.55}.modal-head>button{width:34px;height:34px;border:0;border-radius:7px;color:#56657c;background:#f0f4f9;font-size:24px}.detail-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:22px 0}.detail-metrics>div{display:grid;gap:7px;padding:13px;border-radius:9px;background:#f6f9fd}.detail-metrics span{color:#77859c;font-size:12px}.detail-metrics b,.detail-metrics code{color:#263959;font-size:13px;overflow-wrap:anywhere}.detail-section{margin-top:16px;padding:17px 18px;border:1px solid #e0e8f3;border-radius:10px}.detail-section h3{margin:0;color:#233956;font-size:15px}.detail-section h4{margin:16px 0 0;color:#52647f;font-size:12px}.detail-section p{margin:10px 0 0;color:#5c6b83;font-size:13px;line-height:1.7}.detail-section ul,.detail-section ol{margin:10px 0 0;padding-left:20px;color:#53627a;font-size:13px;line-height:1.75}.detail-section.grid{display:grid;grid-template-columns:1fr 1fr;gap:22px}.detail-section.grid>div+div{border-left:1px solid #e3eaf4;padding-left:22px}.count-config{display:flex;align-items:center;justify-content:space-between;gap:20px}.count-config>div:last-child{display:flex;align-items:center;gap:8px;white-space:nowrap}.count-config input{box-sizing:border-box;width:70px;height:34px;padding:0 8px;border:1px solid #cbd9ed;border-radius:7px;color:#243858}.count-config button{height:34px;padding:0 12px;border:1px solid #b8cef0;border-radius:7px;color:#126ff4;background:#fff;font-weight:650}.empty{padding:35px;color:#77849a;text-align:center}@media(max-width:900px){.detail-metrics,.detail-section.grid{grid-template-columns:1fr}.detail-section.grid>div+div{border-left:0;border-top:1px solid #e3eaf4;margin-top:14px;padding:14px 0 0}.count-config{display:block}.count-config>div:last-child{margin-top:14px}.task-modal,.run-modal{width:min(100%,calc(100vw - 24px));padding:20px}.modal-mask{padding:12px}}
</style>
