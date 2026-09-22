<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../../services/request.js'

const route = useRoute(), router = useRouter()
const recordId = computed(() => route.params.recordId)
const loading = ref(true), error = ref('')
const detail = ref({ record: null, days: [], activeDays: 0, roundNewWords: 0 })
const words = ref({ items: [], total: 0, page: 1, pageSize: 20, totalPages: 0 })
const status = ref('learned'), scope = ref('round'), wordPage = ref(1), wordPageSize = ref(20)
const record = computed(() => detail.value.record || {})
const isOngoing = computed(() => record.value.endedAt === null || record.value.endedAt === undefined)
function value(row, key) { return row?.[key] ?? row?.[key?.toUpperCase?.()] ?? null }
function num(v) { return Number(v || 0).toLocaleString('zh-CN') }
function time(v) { return v ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(v)) : '—' }
function userName() { return value(record.value, 'nickname') || '未设置昵称' }
function userId() { return value(record.value, 'shortId') || value(record.value, 'ownerId') }
function mobile() { return value(record.value, 'mobile') || '未绑定手机号' }
function bookTitle() { return value(record.value, 'bookName') || '未命名词书' }
function level() { return value(record.value, 'levelLabel') || '未分级' }
function roundNo() { return value(record.value, 'roundNo') || 1 }
function percent() { return value(record.value, 'completionPercent') || 0 }
function statusText(v) { return ({ learned: '已学词条', mastered: '已掌握词条', learning: '学习中词条', all: '全部学习过词条' })[v] || '已学词条' }
async function load() {
  loading.value = true; error.value = ''
  try { detail.value = await request(`/admin/study-records/${encodeURIComponent(recordId.value)}`) }
  catch (e) { error.value = e.message || '读取学习记录详情失败' } finally { loading.value = false }
}
async function loadWords(target = wordPage.value) {
  try {
    const params = new URLSearchParams({ page: target, pageSize: wordPageSize.value, status: status.value, scope: scope.value })
    words.value = await request(`/admin/study-records/${encodeURIComponent(recordId.value)}/words?${params.toString()}`)
    wordPage.value = words.value.page
  } catch (e) { error.value = e.message || '读取词条失败' }
}
function chooseStatus(next) { status.value = next; wordPage.value = 1; loadWords(1) }
function chooseScope(next) { scope.value = next; wordPage.value = 1; loadWords(1) }
function turn(target) { if (target < 1 || target > (words.value.totalPages || 1) || target === wordPage.value) return; wordPage.value = target; loadWords(target) }
watch(wordPageSize, () => { wordPage.value = 1; loadWords(1) })
onMounted(() => { load(); loadWords(1) })
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <button class="back" type="button" @click="router.push('/study-records')">‹ 返回学习记录</button>
        <h1>学习记录详情</h1>
        <p>{{ userName() }} · {{ bookTitle() }} · 第 {{ roundNo() }} 轮：这一轮的学习时长、每日明细与词条。</p>
      </div>
      <button class="refresh" :disabled="loading" @click="load(); loadWords(wordPage)">刷新数据</button>
    </div>
    <div v-if="loading && !record.id" class="panel state">正在读取学习记录…</div>
    <div v-else-if="error && !record.id" class="panel state">{{ error }}</div>
    <template v-else>
      <div class="panel summary-panel">
        <div class="summary-head">
          <div><h2>{{ bookTitle() }} · 第 {{ roundNo() }} 轮</h2><p>{{ userName() }} · <span class="code">{{ userId() }}</span> · {{ mobile() }}</p></div>
          <div class="tags"><span class="difficulty">{{ level() }}</span><span v-if="isOngoing" class="pill ongoing">进行中</span><span v-else class="pill ended">已结束</span><span v-if="value(record, 'currentBook')" class="pill current">当前词书</span></div>
        </div>
        <div class="progress-row">
          <div class="progress-main"><strong>{{ num(value(record, 'learnedWords')) }} / {{ num(value(record, 'totalWords')) }}</strong><span>{{ isOngoing ? '当前已学 / 词书总词数' : '结束时已学 / 词书总词数' }}</span></div>
          <div class="bar"><i :style="{ width: Math.min(100, percent()) + '%' }"></i></div>
          <b class="percent">{{ percent() }}%</b>
        </div>
        <div class="metrics">
          <div><span>选择时间</span><b class="time">{{ time(value(record, 'selectedAt')) }}</b></div>
          <div><span>结束时间</span><b class="time">{{ isOngoing ? '进行中' : time(value(record, 'endedAt')) }}</b></div>
          <div><span>开始时已学（带入）</span><b>{{ num(value(record, 'carriedLearnedCount')) }}</b></div>
          <div><span>本轮新学</span><b>{{ num(detail.roundNewWords) }}</b></div>
          <div><span>已掌握</span><b>{{ num(value(record, 'masteredWords')) }}</b></div>
          <div><span>学习中</span><b>{{ num(value(record, 'learningWords')) }}</b></div>
          <div><span>学习天数</span><b>{{ num(value(record, 'studyDayCount')) }} 天</b></div>
          <div><span>学习次数</span><b>{{ num(value(record, 'studyCount')) }}</b></div>
          <div><span>复习次数</span><b>{{ num(value(record, 'reviewedCount')) }}</b></div>
          <div><span>首次学习</span><b class="time">{{ time(value(record, 'firstStudiedAt')) }}</b></div>
          <div><span>最近学习</span><b class="time">{{ time(value(record, 'lastStudiedAt')) }}</b></div>
        </div>
        <p v-if="!isOngoing" class="frozen-note">这一轮已结束（换书时冻结），结束时的已学快照不会再被后来的学习改写。</p>
      </div>

      <div v-if="error" class="error-line">{{ error }} <button type="button" class="link" @click="load()">重试</button></div>

      <div class="panel record-panel">
        <div class="panel-head"><div><h2>每日学习明细</h2><p>本轮窗口内共 {{ num(detail.activeDays) }} 天有学习动作 · 下表 {{ detail.days.length }} 行</p></div></div>
        <div class="table-wrap compact">
          <table>
            <thead><tr><th>日期</th><th>当天新学</th><th>学习次数</th><th>复习次数</th></tr></thead>
            <tbody>
              <tr v-for="day in detail.days" :key="value(day, 'businessDate')">
                <td>{{ value(day, 'businessDate') }}</td>
                <td><b class="new">{{ num(value(day, 'newWordCount')) }}</b> 个</td>
                <td>{{ num(value(day, 'studyCount')) }}</td>
                <td>{{ num(value(day, 'reviewedCount')) }}</td>
              </tr>
              <tr v-if="!detail.days.length"><td colspan="4" class="empty">还没有学习明细</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="panel record-panel">
        <div class="panel-head">
          <div><h2>这本书的词条</h2><p>{{ scope === 'round' ? '本轮新学' : '整本已学' }} · {{ statusText(status) }} · 共 {{ num(words.total) }} 个</p></div>
          <div class="status-switch">
            <button :class="{ active: scope === 'round' }" @click="chooseScope('round')">本轮新学</button>
            <button :class="{ active: scope === 'book' }" @click="chooseScope('book')">整本</button>
          </div>
          <div class="status-switch">
            <button v-for="item in [{ key: 'learned', text: '已学' }, { key: 'mastered', text: '已掌握' }, { key: 'learning', text: '学习中' }, { key: 'all', text: '全部' }]" :key="item.key" :class="{ active: status === item.key }" @click="chooseStatus(item.key)">{{ item.text }}</button>
          </div>
          <label class="page-size">每页 <select v-model.number="wordPageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>单词</th><th>音标</th><th>释义</th><th>难度</th><th>状态</th><th>熟悉度</th><th>生词本</th><th>首次学会</th><th>最近学习</th></tr></thead>
            <tbody>
              <tr v-for="row in words.items" :key="value(row, 'contentId')">
                <td><strong>{{ value(row, 'word') }}</strong></td>
                <td class="phonetic">{{ value(row, 'phonetic') || '—' }}</td>
                <td class="meaning">{{ value(row, 'meaning') || '—' }}</td>
                <td><span class="difficulty">{{ value(row, 'difficultyLabel') || '—' }}</span></td>
                <td><span class="pill" :class="value(row, 'learningStatus') === 'learning' ? 'open-pill' : 'done'">{{ value(row, 'statusLabel') }}</span></td>
                <td>{{ value(row, 'familiarityPercent') === null || value(row, 'familiarityPercent') === undefined ? '—' : value(row, 'familiarityPercent') + '%' }}</td>
                <td>{{ value(row, 'inNotebook') ? '已加入' : '—' }}</td>
                <td>{{ time(value(row, 'firstCompletedAt')) }}</td>
                <td>{{ time(value(row, 'lastFeedbackAt')) }}</td>
              </tr>
              <tr v-if="!words.items.length"><td colspan="9" class="empty">该条件下没有词条</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <span>{{ words.total ? `${(wordPage - 1) * wordPageSize + 1}–${Math.min(words.total, wordPage * wordPageSize)} / ${words.total}` : '0 条' }}</span>
          <button :disabled="wordPage <= 1" @click="turn(wordPage - 1)">‹</button>
          <span>第 {{ wordPage }} / {{ words.totalPages || 1 }} 页</span>
          <button :disabled="wordPage >= (words.totalPages || 1)" @click="turn(wordPage + 1)">›</button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.back{margin-bottom:8px;padding:0;border:0;color:#0d6ff5;background:transparent;font-size:12px}
.refresh{height:40px;padding:0 18px;border:1px solid #c9daf3;border-radius:9px;color:#126ff4;background:#fff}
.state{margin-top:24px;padding:60px;text-align:center;color:#71809a}
.error-line{margin:0 0 13px;padding:12px;border-radius:9px;color:#a33e48;background:#fff0f1}
.summary-panel{margin-top:18px;padding:22px}
.summary-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.summary-head h2{margin:0 0 6px}
.summary-head p{margin:0;color:#7a869d;font-size:12px}
.tags{display:flex;align-items:center;gap:8px}
.difficulty{display:inline-block;padding:4px 9px;border-radius:99px;color:#176fdc;background:#eaf3ff;font-size:12px}
.pill{display:inline-block;padding:4px 9px;border-radius:99px;font-size:12px}
.pill.done{color:#15845f;background:#e8f8f1}
.pill.open-pill{color:#b25a11;background:#fff3e6}
.pill.ongoing{color:#15845f;background:#e8f8f1}
.pill.ended{color:#62718c;background:#eef1f6}
.pill.current{color:#176fdc;background:#eaf3ff}
.code{color:#7c889d;font-variant-numeric:tabular-nums}
.progress-row{display:flex;align-items:center;gap:16px;margin:18px 0 6px}
.progress-main{display:flex;align-items:baseline;gap:8px}
.progress-main strong{color:#11204a;font-size:20px}
.progress-main span{color:#7a869d;font-size:12px}
.bar{flex:1;max-width:420px;height:8px;border-radius:99px;background:#eef3fb;overflow:hidden}
.bar i{display:block;height:100%;border-radius:99px;background:#0d6ff5}
.percent{color:#0d6ff5;font-size:14px}
.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:14px;margin-top:18px;padding-top:16px;border-top:1px solid #eef2f8}
.metrics div{display:flex;flex-direction:column;gap:4px}
.metrics span{color:#7a869d;font-size:12px}
.metrics b{color:#11204a;font-size:14px}
.metrics b.time{font-size:12px;font-variant-numeric:tabular-nums}
.frozen-note{margin:14px 0 0;padding:10px 12px;border-radius:9px;color:#62718c;background:#f8faff;font-size:12px}
.record-panel{margin-top:18px;padding:22px}
.panel-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px;flex-wrap:wrap}
.panel-head h2{margin:0 0 5px}
.panel-head p{margin:0;color:#7a869d;font-size:12px}
.status-switch{display:flex;gap:6px;margin-left:auto}
.status-switch button{height:32px;padding:0 12px;border:1px solid #d7dfeb;border-radius:8px;color:#62718c;background:#fff;font-size:12px}
.status-switch button.active{border-color:#b9d3fb;color:#0d6ff5;background:#eef5ff}
.page-size{color:#6c7890;font-size:12px}
.page-size select{height:32px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}
.table-wrap{position:relative;min-height:180px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}
.table-wrap.compact{max-height:320px}
table{width:100%;border-collapse:collapse}
th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left;font-size:12px;white-space:nowrap}
th{color:#75819a;background:#f8faff}
td{color:#53617a}
td strong{color:#11204a;font-size:14px}
td.phonetic{color:#7c889d}
td.meaning{max-width:320px;white-space:normal}
td b.new{color:#0d6ff5}
.empty{height:130px;text-align:center;color:#8994a7}
.link{padding:4px;border:0;color:#0d6ff5;background:transparent}
.pagination{display:flex;justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}
.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}
.pagination button:disabled{color:#bbc3cf}
</style>
