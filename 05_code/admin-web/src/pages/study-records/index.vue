<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../../services/request.js'

const router = useRouter()
const loading = ref(true), error = ref('')
const keyword = ref(''), query = ref(''), bookId = ref(''), dateFrom = ref(''), dateTo = ref('')
const page = ref(1), pageSize = ref(20), books = ref([])
const result = ref({ items: [], total: 0, page: 1, pageSize: 20, totalPages: 0, summary: {} })
const summary = computed(() => result.value.summary || {})
const hasFilter = computed(() => query.value !== '' || bookId.value !== '' || dateFrom.value !== '' || dateTo.value !== '')
function value(row, key) { return row[key] ?? row[key?.toUpperCase?.()] ?? null }
function num(v) { return Number(v || 0).toLocaleString('zh-CN') }
function time(v) { return v ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(v)) : '—' }
function userName(row) { return value(row, 'nickname') || '未设置昵称' }
function userId(row) { return value(row, 'shortId') || value(row, 'ownerId') || '-' }
function bookTitle(row) { return value(row, 'bookName') || '未命名词书' }
function level(row) { const t = value(row, 'levelLabel'); return t && t !== '未分级' ? t : '' }
function roundText(row) { return `第 ${value(row, 'roundNo') || 1} 轮` }
function progress(row) { return `${num(value(row, 'learnedWords'))} / ${num(value(row, 'totalWords'))}` }
function ongoing(row) { return !value(row, 'endedAt') }
function detailUrl(row) { return `/study-records/${value(row, 'id')}` }
async function load(target = page.value) {
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ page: target, pageSize: pageSize.value })
    if (query.value) params.set('keyword', query.value)
    if (bookId.value) params.set('bookId', bookId.value)
    if (dateFrom.value) params.set('dateFrom', dateFrom.value)
    if (dateTo.value) params.set('dateTo', dateTo.value)
    result.value = await request(`/admin/study-records?${params.toString()}`)
    page.value = result.value.page
  } catch (e) { error.value = e.message || '读取学习记录失败' } finally { loading.value = false }
}
async function loadBooks() {
  try { books.value = await request('/admin/study-records/books') } catch (e) { books.value = [] }
}
function search() { query.value = keyword.value.trim(); page.value = 1; load(1) }
function clearSearch() { keyword.value = ''; query.value = ''; page.value = 1; load(1) }
function applyFilters() { page.value = 1; load(1) }
function reset() { keyword.value = ''; query.value = ''; bookId.value = ''; dateFrom.value = ''; dateTo.value = ''; page.value = 1; load(1) }
function turn(target) { if (target < 1 || target > (result.value.totalPages || 1) || target === page.value) return; page.value = target; load(target) }
function open(row) { router.push(detailUrl(row)) }
watch(pageSize, () => { page.value = 1; if (!loading.value) load(1) })
onMounted(() => { loadBooks(); load(1) })
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>词书学习记录</h1><p>每个用户每本英语词书的学习记录，按「轮次」分开记：一轮 = 一次选定这本词书，换书即冻结当轮，切回同一本书会新开一轮并带入上一轮的已学/未学。</p></div><button class="refresh" :disabled="loading" @click="load(page)">刷新数据</button></div>
    <div v-if="loading && !result.items.length" class="panel state">正在读取学习记录…</div>
    <template v-else>
      <div class="panel record-panel">
        <div class="panel-head">
          <div><h2>学习记录列表</h2><p>共 {{ result.total }} 条记录（含历史轮次）</p></div>
          <button v-if="hasFilter" class="plain" @click="reset">清除筛选</button>
        </div>
        <div class="toolbar">
          <form @submit.prevent="search">
            <input v-model="keyword" maxlength="60" placeholder="搜索昵称、用户编号、手机号或词书名"/>
            <button :disabled="loading">{{ loading ? '搜索中…' : '搜索' }}</button>
            <button v-if="query" type="button" class="plain" @click="clearSearch">清除</button>
          </form>
          <label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label>
        </div>
        <div class="range-row">
          <label class="picker">词书<select v-model="bookId" @change="applyFilters"><option value="">全部词书</option><option v-for="book in books" :key="book.id" :value="book.id">{{ book.name }}</option></select></label>
          <label class="picker">选择时间<input type="date" v-model="dateFrom" @change="applyFilters"/></label>
          <span class="tilde">至</span>
          <label class="picker"><input type="date" v-model="dateTo" @change="applyFilters"/></label>
          <div class="range-summary">
            <span>记录 <b>{{ num(summary.records) }}</b></span>
            <span>用户 <b>{{ num(summary.users) }}</b></span>
            <span>词书 <b>{{ num(summary.books) }}</b></span>
            <span>进行中 <b>{{ num(summary.ongoing) }}</b></span>
            <span>今天有学习 <b>{{ num(summary.todayStudied) }}</b></span>
          </div>
        </div>
        <div v-if="error" class="error-line">{{ error }} <button type="button" class="link" @click="load(page)">重试</button></div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>昵称</th><th>用户编号</th><th>词书</th><th>轮次</th><th>选择时间</th><th>学习进度</th><th>学习天数</th><th>学习次数</th><th>复习次数</th><th>最近学习</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in result.items" :key="value(row, 'id')">
                <td><strong>{{ userName(row) }}</strong></td>
                <td><span class="code">{{ userId(row) }}</span></td>
                <td><span class="book">{{ bookTitle(row) }}</span><small v-if="level(row)">{{ level(row) }}</small><small v-if="value(row, 'currentBook')" class="current">当前词书</small></td>
                <td><span class="round">{{ roundText(row) }}</span><small v-if="ongoing(row)" class="ongoing">进行中</small><small v-else class="ended">已结束</small></td>
                <td>{{ time(value(row, 'selectedAt')) }}</td>
                <td>
                  <div class="progress-cell"><span>{{ progress(row) }}</span><b>{{ value(row, 'completionPercent') }}%</b></div>
                  <div class="bar"><i :style="{ width: Math.min(100, value(row, 'completionPercent') || 0) + '%' }"></i></div>
                  <small v-if="value(row, 'carriedLearnedCount') > 0">带入 {{ num(value(row, 'carriedLearnedCount')) }} 个已学</small>
                </td>
                <td>{{ num(value(row, 'studyDayCount')) }} 天</td>
                <td>{{ num(value(row, 'studyCount')) }}</td>
                <td>{{ num(value(row, 'reviewedCount')) }}</td>
                <td>{{ time(value(row, 'lastStudiedAt')) }}</td>
                <td><div class="row-actions"><button class="link" @click="open(row)">查看详情 ›</button></div></td>
              </tr>
              <tr v-if="!result.items.length"><td colspan="11" class="empty">该条件下还没有词书学习记录</td></tr>
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
.toolbar button{height:38px;padding:0 16px;border:0;border-radius:8px;color:#fff;background:#0d6ff5}
.toolbar label{color:#6c7890;font-size:12px}
.toolbar select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}
.range-row{display:flex;align-items:center;gap:16px;flex-wrap:wrap;margin-bottom:13px}
.picker{display:flex;align-items:center;gap:10px;color:#667590;font-size:12px}
.picker select{width:180px;height:38px;padding:0 10px;border:1px solid #d7dfeb;border-radius:8px;background:#fff}
.picker input{width:150px;height:38px;padding:0 10px;border:1px solid #d7dfeb;border-radius:8px;background:#fff}
.tilde{color:#a5aec0;font-size:12px}
.range-summary{display:flex;flex-wrap:wrap;gap:16px;margin-left:auto;color:#667590;font-size:12px}
.range-summary span{display:flex;align-items:center;gap:6px}
.range-summary b{color:#11204a}
.table-wrap{position:relative;min-height:220px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}
table{width:100%;border-collapse:collapse}
th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left;font-size:12px;white-space:nowrap}
th{color:#75819a;background:#f8faff}
td{color:#53617a}
td strong{display:block;color:#11204a;font-size:14px}
td small{display:block;margin-top:4px;color:#7c889d;font-size:11px}
td small.current{color:#176fdc}
td small.ongoing{color:#15845f}
td small.ended{color:#8b96a8}
.code{color:#7c889d;font-variant-numeric:tabular-nums}
.book{color:#11204a;font-size:13px}
.round{color:#11204a;font-size:13px}
.progress-cell{display:flex;align-items:center;gap:8px}
.progress-cell b{color:#0d6ff5}
.bar{width:120px;height:6px;margin-top:6px;border-radius:99px;background:#eef3fb;overflow:hidden}
.bar i{display:block;height:100%;border-radius:99px;background:#0d6ff5}
.row-actions{display:flex;align-items:center;gap:8px}
.link{padding:4px;border:0;color:#0d6ff5;background:transparent;white-space:nowrap}
.empty{height:170px;text-align:center;color:#8994a7}
.loading{position:absolute;inset:42px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}
.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}
.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}
.pagination button:disabled{color:#bbc3cf}
</style>
