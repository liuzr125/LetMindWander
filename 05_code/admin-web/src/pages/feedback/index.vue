<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'

const loading = ref(true), error = ref('')
const state = ref('all'), keyword = ref(''), query = ref(''), page = ref(1), pageSize = ref(20)
const result = ref({ items: [], total: 0, page: 1, pageSize: 20, summary: {} })
const detail = ref(null), detailLoading = ref(false), savingId = ref('')
const summary = computed(() => result.value.summary || {})
const stateText = computed(() => ({ all: '全部用户反馈', open: '待处理的用户反馈', handled: '已处理的用户反馈' })[state.value] || '全部用户反馈')
const hasFilter = computed(() => state.value !== 'all' || query.value !== '')
function value(row, key) { return row[key] ?? row[key?.toUpperCase?.()] ?? null }
function num(value) { return Number(value || 0).toLocaleString('zh-CN') }
function time(value) { return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) : '—' }
function userName(row) { return value(row, 'nickname') || '未设置昵称' }
function userId(row) { return value(row, 'shortId') || value(row, 'ownerId') || '-' }
function mobile(row) { return value(row, 'mobile') || '未绑定手机号' }
function stateLabel(row) { return value(row, 'stateLabel') || (value(row, 'state') === 'handled' ? '已处理' : '待处理') }
function isOpen(row) { return value(row, 'state') !== 'handled' }
async function load(target = page.value) {
  loading.value = true; error.value = ''
  try {
    result.value = await request(`/admin/feedback?page=${target}&pageSize=${pageSize.value}&state=${state.value}&keyword=${encodeURIComponent(query.value)}`)
    page.value = result.value.page
  } catch (e) { error.value = e.message || '读取用户反馈失败' } finally { loading.value = false }
}
function search() { query.value = keyword.value.trim(); page.value = 1; load(1) }
function clearSearch() { keyword.value = ''; query.value = ''; page.value = 1; load(1) }
function chooseState(target) { state.value = target; page.value = 1; load(1) }
function reset() { state.value = 'all'; clearSearch() }
function turn(target) { if (target < 1 || target > (result.value.totalPages || 1) || target === page.value) return; page.value = target; load(target) }
async function showDetail(row) {
  detailLoading.value = true; error.value = ''
  try { detail.value = await request(`/admin/feedback/${encodeURIComponent(value(row, 'id'))}`) }
  catch (e) { error.value = e.message || '读取反馈详情失败' } finally { detailLoading.value = false }
}
async function setState(row, next) {
  if (next === 'handled' && !window.confirm(`把「${userName(row)}」的这条反馈标记为已处理？\n标记后概览页的待处理事项会相应减少。`)) return
  savingId.value = value(row, 'id'); error.value = ''
  try {
    const updated = await request(`/admin/feedback/${encodeURIComponent(value(row, 'id'))}/state`, { method: 'PUT', body: JSON.stringify({ state: next }) })
    if (detail.value && value(detail.value, 'id') === value(updated, 'id')) detail.value = updated
    await load(page.value)
  } catch (e) { error.value = e.message || '更新反馈状态失败' } finally { savingId.value = '' }
}
watch(pageSize, () => { page.value = 1; if (!loading.value) load(1) })
onMounted(() => load(1))
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>用户反馈</h1><p>用户在小程序「我的 → 问题反馈」提交的问题、建议与内容纠错，可在此查看正文与详情并标记处理状态。</p></div><button class="refresh" :disabled="loading" @click="load(page)">刷新数据</button></div>
    <div v-if="loading && !result.items.length" class="panel state">正在读取用户反馈…</div>
    <template v-else>
      <div class="panel record-panel">
        <div class="panel-head">
          <div><h2>反馈列表</h2><p>{{ stateText }} · 共 {{ result.total }} 条</p></div>
          <button v-if="hasFilter" class="plain" @click="reset">清除筛选</button>
        </div>
        <div class="toolbar">
          <form @submit.prevent="search">
            <input v-model="keyword" maxlength="80" placeholder="搜索昵称、手机号或反馈正文"/>
            <button :disabled="loading">{{ loading ? '搜索中…' : '搜索' }}</button>
            <button v-if="query" type="button" class="plain" @click="clearSearch">清除</button>
          </form>
          <label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label>
        </div>
        <div class="range-row">
          <label class="picker">状态筛选<select :value="state" @change="chooseState($event.target.value)"><option value="all">全部状态</option><option value="open">待处理</option><option value="handled">已处理</option></select></label>
          <div class="range-summary">
            <span>待处理 <b>{{ num(summary.open) }}</b></span>
            <span>已处理 <b>{{ num(summary.handled) }}</b></span>
            <span>总计 <b>{{ num(summary.total) }}</b></span>
          </div>
        </div>
        <div v-if="error" class="error-line">{{ error }} <button type="button" class="link" @click="load(page)">重试</button></div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>提交时间</th><th>昵称</th><th>用户编号</th><th>手机号</th><th>类型</th><th>反馈内容</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in result.items" :key="value(row, 'id')">
                <td>{{ time(value(row, 'createdAt')) }}</td>
                <td><strong>{{ userName(row) }}</strong></td>
                <td><span class="code">{{ userId(row) }}</span></td>
                <td>{{ mobile(row) }}</td>
                <td><span class="difficulty">{{ value(row, 'categoryLabel') || value(row, 'category') }}</span></td>
                <td>{{ value(row, 'excerpt') || '—' }}</td>
                <td><span class="pill" :class="isOpen(row) ? 'open' : 'done'">{{ stateLabel(row) }}</span></td>
                <td><div class="row-actions">
                  <button class="link" @click="showDetail(row)">查看详情 ›</button>
                  <button v-if="isOpen(row)" type="button" class="outline-button" :disabled="savingId === value(row, 'id')" @click="setState(row, 'handled')">{{ savingId === value(row, 'id') ? '处理中…' : '标记已处理' }}</button>
                  <button v-else type="button" class="outline-button" :disabled="savingId === value(row, 'id')" @click="setState(row, 'open')">重新打开</button>
                </div></td>
              </tr>
              <tr v-if="!result.items.length"><td colspan="8" class="empty">该条件下没有用户反馈</td></tr>
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

    <div v-if="detail" class="modal-mask" @click.self="detail = null">
      <div class="feedback-dialog">
        <header>
          <div><h2>反馈详情</h2><small>{{ value(detail, 'id') }}</small></div>
          <span class="pill" :class="isOpen(detail) ? 'open' : 'done'">{{ stateLabel(detail) }}</span>
        </header>
        <dl>
          <dt>提交用户</dt><dd>{{ userName(detail) }}（{{ userId(detail) }}）</dd>
          <dt>手机号</dt><dd>{{ mobile(detail) }}</dd>
          <dt>反馈类型</dt><dd>{{ value(detail, 'categoryLabel') || value(detail, 'category') }}</dd>
          <dt>提交时间</dt><dd>{{ time(value(detail, 'createdAt')) }}</dd>
          <dt>更新时间</dt><dd>{{ time(value(detail, 'updatedAt')) }}</dd>
          <dt v-if="value(detail, 'contentTitle')">关联内容</dt><dd v-if="value(detail, 'contentTitle')">{{ value(detail, 'contentTitle') }}（{{ value(detail, 'contentId') }}）</dd>
          <dt v-if="value(detail, 'requestId')">请求标识</dt><dd v-if="value(detail, 'requestId')">{{ value(detail, 'requestId') }}</dd>
        </dl>
        <div class="feedback-body"><h3>反馈正文</h3><p>{{ value(detail, 'body') || '（无正文）' }}</p></div>
        <footer>
          <button type="button" class="refresh" @click="detail = null">关闭</button>
          <button v-if="isOpen(detail)" class="primary-button" :disabled="savingId === value(detail, 'id')" @click="setState(detail, 'handled')">{{ savingId === value(detail, 'id') ? '处理中…' : '标记已处理' }}</button>
          <button v-else class="primary-button" :disabled="savingId === value(detail, 'id')" @click="setState(detail, 'open')">重新打开</button>
        </footer>
      </div>
    </div>
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
.toolbar button{height:38px;padding:0 16px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}
.toolbar label{color:#6c7890;font-size:12px}
.toolbar select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}
.range-row{display:flex;align-items:center;gap:26px;flex-wrap:wrap;margin-bottom:13px}
.picker{display:flex;align-items:center;gap:10px;color:#667590;font-size:12px}
.picker select{width:170px;height:38px;padding:0 10px;border:1px solid #d7dfeb;border-radius:8px;background:#fff}
.range-summary{display:flex;flex-wrap:wrap;gap:16px;color:#667590;font-size:12px}
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
.code{color:#7c889d;font-variant-numeric:tabular-nums}
.difficulty{display:inline-block;padding:4px 9px;border-radius:99px;color:#176fdc;background:#eaf3ff;font-size:12px}
.pill{display:inline-block;padding:4px 9px;border-radius:99px;font-size:12px}
.pill.open{color:#b25a11;background:#fff3e6}
.pill.done{color:#15845f;background:#e8f8f1}
.row-actions{display:flex;align-items:center;gap:8px}
.link{padding:4px;border:0;color:#0d6ff5;background:transparent;white-space:nowrap}
.outline-button{height:29px;padding:0 9px;border:1px solid #b9d3fb;border-radius:7px;color:#0d6ff5;background:#fff;white-space:nowrap}
.empty{height:170px;text-align:center;color:#8994a7}
.loading{position:absolute;inset:42px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}
.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}
.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}
.pagination button:disabled{color:#bbc3cf}
.feedback-dialog{width:min(100%,720px);max-height:86vh;overflow:auto;display:grid;gap:14px;padding:26px;border-radius:14px;background:#fff;box-shadow:0 18px 50px rgba(24,38,65,.24)}
.feedback-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.feedback-dialog h2{margin:0;font-size:19px}
.feedback-dialog header small{color:#8b96a8;font-variant-numeric:tabular-nums}
.feedback-dialog dl{display:grid;grid-template-columns:96px 1fr;gap:8px 16px;margin:0}
.feedback-dialog dt{color:#71809a;font-size:13px}
.feedback-dialog dd{margin:0;color:#17203b;font-size:13px;word-break:break-all}
.feedback-body{padding:14px 16px;border:1px solid #e4ecf7;border-radius:12px;background:#f8fbff}
.feedback-body h3{margin:0 0 8px;color:#2c4a76;font-size:13px}
.feedback-body p{margin:0;color:#17203b;font-size:14px;line-height:1.7;white-space:pre-wrap}
.feedback-dialog footer{display:flex;justify-content:flex-end;gap:10px}
</style>
