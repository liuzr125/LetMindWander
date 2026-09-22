<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'

function today() { return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()) }
function daysAgo(days) { const base = new Date(`${today()}T00:00:00+08:00`); base.setDate(base.getDate() - days); return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(base) }
function display(value) { return value ? value.replaceAll('-', '/') : '—' }

const loading = ref(true), error = ref('')
const dateFrom = ref(today()), dateTo = ref(today()), preset = ref('today')
const keyword = ref(''), query = ref(''), page = ref(1), pageSize = ref(20)
const result = ref({ items: [], total: 0, page: 1, pageSize: 20, summary: {} })
const summary = computed(() => result.value.summary || {})
const hasFilter = computed(() => preset.value !== 'today' || query.value !== '')
const rangeText = computed(() => `${display(dateFrom.value)} ~ ${display(dateTo.value)}`)
function value(row, key) { return row[key] ?? row[key?.toUpperCase?.()] ?? null }
function num(value) { return Number(value || 0).toLocaleString('zh-CN') }
function amount(value) { return Number(value || 0).toFixed(4) }
function userName(row) { return value(row, 'nickname') || '未设置昵称' }
function userId(row) { return value(row, 'shortId') || value(row, 'userId') || '-' }
function mobile(row) { return value(row, 'mobile') || '未绑定手机号' }
function usage(row) { return `${num(value(row, 'usedCount'))} / ${num(value(row, 'limitCount'))}` }
function usageRate(row) { const used = Number(value(row, 'usedCount') || 0), limit = Number(value(row, 'limitCount') || 0); return limit ? Math.min(100, Math.round((used / limit) * 100)) : 0 }
async function load(target = page.value) {
  loading.value = true; error.value = ''
  try {
    result.value = await request(`/admin/ai/usage-logs?page=${target}&pageSize=${pageSize.value}&dateFrom=${dateFrom.value}&dateTo=${dateTo.value}&keyword=${encodeURIComponent(query.value)}`)
    page.value = result.value.page
  } catch (e) { error.value = e.message || '读取用量日志失败' } finally { loading.value = false }
}
function search() { query.value = keyword.value.trim(); page.value = 1; load(1) }
function clearSearch() { keyword.value = ''; query.value = ''; page.value = 1; load(1) }
function range(days, name) { dateTo.value = today(); dateFrom.value = daysAgo(days - 1); preset.value = name; page.value = 1; load(1) }
function reset() { dateFrom.value = today(); dateTo.value = today(); preset.value = 'today'; clearSearch() }
function turn(target) { if (target < 1 || target > (result.value.totalPages || 1) || target === page.value) return; page.value = target; load(target) }
watch(pageSize, () => { page.value = 1; if (!loading.value) load(1) })
onMounted(() => load(1))
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>AI 用量日志</h1><p>按天 × 用户记录每日 AI 用量：额度消耗、成功失败次数、token 与费用。</p></div><button class="refresh" :disabled="loading" @click="load(page)">刷新数据</button></div>
    <div v-if="loading && !result.items.length" class="panel state">正在读取用量日志…</div>
    <template v-else>
      <div class="panel record-panel">
        <div class="panel-head">
          <div><h2>用量明细</h2><p>{{ rangeText }} · 共 {{ result.total }} 条记录</p></div>
          <button v-if="hasFilter" class="plain" @click="reset">清除筛选</button>
        </div>
        <div class="toolbar">
          <form @submit.prevent="search">
            <input v-model="dateFrom" type="date" :max="dateTo" aria-label="开始日期"/>
            <input v-model="dateTo" type="date" :min="dateFrom" :max="today()" aria-label="结束日期"/>
            <input v-model="keyword" maxlength="80" placeholder="搜索昵称、手机号、短 ID 或用户 ID"/>
            <button :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
            <button v-if="query" type="button" class="plain" @click="clearSearch">清除</button>
          </form>
          <label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label>
        </div>
        <div class="range-row">
          <button type="button" class="range" :class="{ active: preset === 'today' }" @click="range(1, 'today')">今天</button>
          <button type="button" class="range" :class="{ active: preset === 'week' }" @click="range(7, 'week')">近 7 天</button>
          <button type="button" class="range" :class="{ active: preset === 'month' }" @click="range(30, 'month')">近 30 天</button>
          <div class="range-summary">
            <span>活跃用户（用户·天） <b>{{ num(summary.userDays) }}</b></span>
            <span>用量 / 额度 <b>{{ num(summary.usedTotal) }} / {{ num(summary.limitTotal) }}</b></span>
            <span>成功 / 失败 <b>{{ num(summary.succeededTotal) }} / {{ num(summary.failedTotal) }}</b></span>
            <span>输入 tokens <b>{{ num(summary.inputTokens) }}</b></span>
            <span>输出 tokens <b>{{ num(summary.outputTokens) }}</b></span>
            <span>费用合计 <b>¥{{ amount(summary.costTotal) }}</b></span>
            <span>当前参数上限 <b>个人 {{ num(summary.personalLimit) }} · 全站 {{ num(summary.globalLimit) }}</b></span>
          </div>
        </div>
        <div v-if="error" class="error-line">{{ error }} <button type="button" class="link" @click="load(page)">重试</button></div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>日期</th><th>昵称</th><th>用户编号</th><th>手机号</th><th>已用 / 上限</th><th>额度占用</th><th>成功</th><th>失败</th><th>处理中</th><th>输入 tokens</th><th>输出 tokens</th><th>费用（¥）</th></tr></thead>
            <tbody>
              <tr v-for="row in result.items" :key="`${value(row,'quotaDate')}-${value(row,'userId')}`">
                <td>{{ display(value(row, 'quotaDate')) }}</td>
                <td><strong>{{ userName(row) }}</strong></td>
                <td><span class="code">{{ userId(row) }}</span></td>
                <td>{{ mobile(row) }}</td>
                <td>{{ usage(row) }}</td>
                <td><div class="usage-bar" :title="`已用 ${usageRate(row)}%`"><i :style="{ width: `${usageRate(row)}%` }" :class="{ warn: usageRate(row) >= 80 }"></i></div></td>
                <td>{{ num(value(row, 'succeededCount')) }}</td>
                <td>{{ num(value(row, 'failedCount')) }}</td>
                <td>{{ num(value(row, 'runningCount')) }}</td>
                <td>{{ num(value(row, 'inputTokens')) }}</td>
                <td>{{ num(value(row, 'outputTokens')) }}</td>
                <td>{{ amount(value(row, 'costCny')) }}</td>
              </tr>
              <tr v-if="!result.items.length"><td colspan="12" class="empty">该时间段没有 AI 用量记录</td></tr>
            </tbody>
          </table>
          <div v-if="loading" class="loading">正在加载…</div>
        </div>
        <div class="pagination">
          <span>{{ result.total ? `${(page - 1) * pageSize + 1}–${Math.min(result.total, page * pageSize)} / ${result.total}` : '0 条' }}</span>
          <button :disabled="page <= 1" @click="turn(page - 1)">‹</button>
          <span>第 {{ page }} / {{ result.totalPages || 1 }} 页</span>
          <button :disabled="page >= (result.totalPages || 1)" @click="turn(page + 1)">›</button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.refresh{height:40px;padding:0 18px;border:1px solid #c9daf3;border-radius:9px;color:#126ff4;background:#fff}
.state{margin-top:24px;padding:60px;text-align:center;color:#71809a}
.error-line{margin:0 0 13px;padding:12px;border-radius:9px;color:#a33e48;background:#fff0f1}
.record-panel{margin-top:18px;padding:22px}
.panel-head,.toolbar,.pagination{display:flex;align-items:center;justify-content:space-between}
.panel-head h2{margin:0 0 5px}
.panel-head p{margin:0;color:#7a869d;font-size:12px}
.plain{border:1px solid #d7dfeb!important;color:#62718c!important;background:#fff!important}
.toolbar{margin:18px 0 13px}
.toolbar form{display:flex;flex-wrap:wrap;gap:8px}
.toolbar input{width:340px;height:38px;padding:0 11px;border:1px solid #d7dfeb;border-radius:8px}
.toolbar input[type=date]{width:150px}
.toolbar button{height:38px;padding:0 16px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}
.toolbar label{color:#6c7890;font-size:12px}
.toolbar select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}
.range-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:13px}
.range{height:34px;padding:0 14px;border:1px solid #c9daf3;border-radius:8px;color:#126ff4;background:#fff}
.range.active{border-color:#0d6ff5;color:#fff;background:#0d6ff5}
.range-summary{display:flex;flex-wrap:wrap;gap:16px;margin-left:6px;color:#667590;font-size:12px}
.range-summary span{display:flex;align-items:center;gap:6px}
.range-summary b{color:#11204a}
.table-wrap{position:relative;min-height:220px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}
table{width:100%;border-collapse:collapse}
th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left;font-size:12px}
th{color:#75819a;background:#f8faff;white-space:nowrap}
td{color:#53617a}
td strong,td small{display:block}
td strong{color:#11204a;font-size:14px}
td small{margin-top:5px;color:#7c889d}
.empty{height:170px;text-align:center;color:#8994a7}
.loading{position:absolute;inset:42px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}
.code{color:#7c889d;font-variant-numeric:tabular-nums}
.usage-bar{width:96px;height:8px;border-radius:999px;background:#e6eefb;overflow:hidden}
.usage-bar i{display:block;height:100%;border-radius:999px;background:linear-gradient(90deg,#4ea1ff,#0869f7)}
.usage-bar i.warn{background:linear-gradient(90deg,#ffb35c,#ff7a45)}
.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}
.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}
.pagination button:disabled{color:#bbc3cf}
</style>
