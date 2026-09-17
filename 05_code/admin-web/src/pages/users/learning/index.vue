<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../../../services/request.js'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const detail = ref(null)
const notebookPage = ref(1)
const notebookPageSize = ref(20)

const userId = computed(() => String(route.params.userId || ''))
const notebookItems = computed(() => detail.value?.notebookItems?.items || [])
const notebookTotal = computed(() => Number(detail.value?.notebook?.totalCount || 0))
const notebookPages = computed(() => Math.max(1, Math.ceil(notebookTotal.value / notebookPageSize.value)))

function statusText(value) { return value === 'active' ? '正常' : value === 'disabled' ? '已停用' : (value || '未知') }
function difficultyText(value) { return value === 'intro' ? '入门' : value === 'advanced' ? '进阶' : (value || '—') }
function learningStatusText(value) { return ({ NOT_STARTED:'未开始', LEARNING:'学习中', MASTERED:'已掌握' })[value] || value || '未开始' }
function weekdayText(mask) {
  const days = ['一','二','三','四','五','六','日']
  const selected = days.filter((_, index) => (Number(mask || 0) & (1 << index)) !== 0)
  return selected.length === 7 ? '每天' : selected.length ? `周${selected.join('、')}` : '未设置'
}
function fmt(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { timeZone:'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hour12:false }).format(new Date(value)).replaceAll('/', '-')
}

async function load() {
  if (!userId.value) return
  loading.value = true
  error.value = ''
  try {
    detail.value = await request(`/admin/users/${encodeURIComponent(userId.value)}/learning?notebookPage=${notebookPage.value}&notebookPageSize=${notebookPageSize.value}`)
  } catch (e) {
    error.value = e.message || '用户学习信息加载失败'
  } finally {
    loading.value = false
  }
}

function changePageSize() { notebookPage.value = 1; load() }
function turnPage(target) { if (target < 1 || target > notebookPages.value || target === notebookPage.value) return; notebookPage.value = target; load() }
function tokenUpdated() { load() }

watch(userId, () => { notebookPage.value = 1; load() })
onMounted(() => { window.addEventListener('admin-token-updated', tokenUpdated); load() })
onBeforeUnmount(() => window.removeEventListener('admin-token-updated', tokenUpdated))
</script>

<template>
  <section class="learning-page">
    <div class="page-heading">
      <div>
        <button class="back" @click="router.push('/users')">‹ 返回账号列表</button>
        <h1>用户学习详情</h1>
        <p>只读查看学习计划、词书进度和生词本，不展示 OpenID、AI 对话或日记正文。</p>
      </div>
      <button class="refresh" :disabled="loading" @click="load">↻ 刷新</button>
    </div>

    <div v-if="error" class="panel state error"><strong>暂时无法读取用户学习信息</strong><span>{{ error }}</span><button @click="load">重新加载</button></div>
    <div v-else-if="loading && !detail" class="panel state">正在读取用户学习信息…</div>

    <template v-else-if="detail">
      <section class="panel identity">
        <div class="avatar">{{ (detail.account.nickname || '?').slice(0,1) }}</div>
        <div class="identity-main"><span>学习账号</span><strong>{{ detail.account.nickname || '未设置昵称' }}</strong><small>{{ detail.account.shortId || (detail.account.seqNo ? `#${detail.account.seqNo}` : detail.account.userId) }}</small></div>
        <dl><div><dt>手机号</dt><dd>{{ detail.account.mobileMasked || '未绑定' }}</dd></div><div><dt>账号状态</dt><dd><i :class="['status',detail.account.status]">{{ statusText(detail.account.status) }}</i></dd></div><div><dt>最后登录</dt><dd>{{ fmt(detail.account.lastLoginAt) }}</dd></div><div><dt>AI 授权</dt><dd>{{ detail.account.aiConsented ? '已同意' : '未同意' }}</dd></div></dl>
      </section>

      <div class="summary-grid">
        <article class="panel summary"><span>今日任务</span><strong>{{ detail.today.completedCount }} / {{ detail.today.totalCount }}</strong><small>完成率 {{ detail.today.completionRate }}% · 进行中 {{ detail.today.doingCount }}</small></article>
        <article class="panel summary"><span>当前词书</span><strong>{{ detail.vocabulary?.bookName || '尚未选择' }}</strong><small v-if="detail.vocabulary">已学 {{ detail.vocabulary.learnedCount }} · 剩余 {{ detail.vocabulary.remainingCount }}</small><small v-else>用户学习前需先选择词书</small></article>
        <article class="panel summary"><span>生词本</span><strong>{{ detail.notebook.totalCount }} 词</strong><small>当前到期 {{ detail.notebook.dueCount }} 词</small></article>
      </div>

      <div class="detail-grid">
        <section class="panel block plan-block">
          <header><div><h2>学习计划</h2><p>展示用户当前已保存并生效的计划。</p></div><span v-if="detail.plan" :class="['plan-state',{paused:detail.plan.paused}]">{{ detail.plan.paused ? '已暂停' : '执行中' }}</span></header>
          <div v-if="detail.plan" class="plan-content">
            <div class="budget"><strong>{{ detail.plan.dailyBudgetMin }}</strong><span>分钟 / 日</span><small>{{ weekdayText(detail.plan.weekdaysMask) }} · {{ difficultyText(detail.plan.difficulty) }}</small></div>
            <dl class="plan-values"><div><dt>技术新学</dt><dd>{{ detail.plan.techCount }} 条</dd></div><div><dt>英语新词</dt><dd>{{ detail.plan.newWordCount }} 个</dd></div><div><dt>到期复习</dt><dd>{{ detail.plan.reviewEnabled ? `开启，上限 ${detail.plan.reviewLimit}` : '关闭' }}</dd></div><div><dt>今日复盘</dt><dd>{{ detail.plan.journalEnabled ? '开启' : '关闭' }}</dd></div></dl>
            <div class="topics"><span>技术主题</span><b v-for="topic in detail.plan.topics" :key="topic.id">{{ topic.name }}</b><em v-if="!detail.plan.topics?.length">未选择</em></div>
          </div>
          <div v-else class="empty-card"><strong>尚未保存学习计划</strong><span>这里不会用前端默认值冒充用户真实设置。</span></div>
        </section>

        <section class="panel block vocabulary-block">
          <header><div><h2>单词进度</h2><p>仅展示用户当前选择的词书。</p></div></header>
          <template v-if="detail.vocabulary">
            <div class="book-title"><div><span>当前词书</span><strong>{{ detail.vocabulary.bookName }}</strong><small>{{ detail.vocabulary.bookCode }}</small></div><b>{{ detail.vocabulary.completionRate }}%</b></div>
            <div class="progress"><i :style="{width:`${Math.min(100,Math.max(0,detail.vocabulary.completionRate || 0))}%`}"></i></div>
            <div class="book-stats"><div><strong>{{ detail.vocabulary.learnedCount }}</strong><span>已学</span></div><div><strong>{{ detail.vocabulary.remainingCount }}</strong><span>还剩</span></div><div><strong>{{ detail.vocabulary.totalCount }}</strong><span>总词数</span></div></div>
            <p class="estimate">每天 {{ detail.vocabulary.dailyNewCount }} 个新词，预计还需 <b>{{ detail.vocabulary.estimatedRemainingDays }}</b> 天</p>
          </template>
          <div v-else class="empty-card"><strong>尚未选择词书</strong><span>没有词书时不计算虚假的总量和预计天数。</span></div>
        </section>
      </div>

      <section class="panel notebook-block">
        <header><div><h2>生词本</h2><p>查看用户主动加入的单词及当前学习状态。</p></div><label>每页 <select v-model.number="notebookPageSize" @change="changePageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 个</label></header>
        <div class="table-wrap">
          <table><thead><tr><th>单词</th><th>音标</th><th>释义</th><th>学习状态</th><th>熟悉度</th><th>复习状态</th></tr></thead><tbody>
            <tr v-for="item in notebookItems" :key="item.contentId"><td><strong>{{ item.wordTerm || item.title }}</strong></td><td>{{ item.phonetic || '—' }}</td><td class="meaning">{{ item.meaning || '暂未收录释义' }}</td><td><span class="tag">{{ learningStatusText(item.learningStatus) }}</span></td><td>{{ item.familiarityPercent ?? 0 }}%</td><td>{{ item.inReview ? '复习中' : '未加入复习' }}</td></tr>
            <tr v-if="!notebookItems.length"><td colspan="6" class="empty">生词本暂无单词</td></tr>
          </tbody></table>
          <div v-if="loading" class="loading-mask">正在刷新…</div>
        </div>
        <div class="pagination"><span>{{ notebookTotal ? `${(notebookPage-1)*notebookPageSize+1}–${Math.min(notebookTotal,notebookPage*notebookPageSize)} / ${notebookTotal}` : '0 条' }}</span><button :disabled="notebookPage<=1" @click="turnPage(notebookPage-1)">‹</button><span>{{ notebookPage }} / {{ notebookPages }}</span><button :disabled="notebookPage>=notebookPages" @click="turnPage(notebookPage+1)">›</button></div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.back{display:block;margin:0 0 10px;padding:0;border:0;color:#1474f8;background:transparent;font-size:13px}.refresh{height:40px;padding:0 17px;border:1px solid #bad3fb;border-radius:8px;color:#0b6ff5;background:#fff}.state{margin-top:22px;padding:60px;text-align:center;color:#74819a}.state strong,.state span{display:block}.state span{margin-top:8px}.state button{margin-top:16px;padding:8px 16px;border:1px solid #e4b8bd;border-radius:7px;color:#b93c48;background:#fff}.state.error{color:#bf3f4b}.identity{display:flex;align-items:center;gap:16px;margin-top:22px;padding:20px 24px}.avatar{width:58px;height:58px;display:grid;place-items:center;border-radius:50%;color:#1474f8;background:#e8f2ff;font-size:24px;font-weight:700}.identity-main{min-width:190px}.identity-main span,.identity-main strong,.identity-main small{display:block}.identity-main span,.identity-main small{color:#79869d;font-size:12px}.identity-main strong{margin:5px 0;color:#13234a;font-size:20px}.identity dl{flex:1;display:grid;grid-template-columns:repeat(4,1fr);margin:0}.identity dl div{padding:0 20px;border-left:1px solid #e8edf4}.identity dt{color:#8490a4;font-size:11px}.identity dd{margin:7px 0 0;color:#42516d;font-size:13px}.status,.plan-state{display:inline-flex;padding:4px 9px;border-radius:99px;color:#13845f;background:#e8f8f1;font-size:12px;font-style:normal}.status.disabled,.plan-state.paused{color:#b4404a;background:#fff0f1}.summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:18px}.summary{padding:20px 22px}.summary span,.summary strong,.summary small{display:block}.summary span{color:#76839a;font-size:12px}.summary strong{margin:10px 0 7px;color:#11204a;font-size:23px}.summary small{color:#71809a}.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}.block,.notebook-block{padding:22px}.block header,.notebook-block header{display:flex;align-items:flex-start;justify-content:space-between}.block h2,.notebook-block h2{margin:0;font-size:18px}.block header p,.notebook-block header p{margin:5px 0 0;color:#7b879c;font-size:12px}.plan-content{display:flex;gap:24px;margin-top:22px}.budget{width:125px;padding:18px;border-radius:10px;background:#eff6ff}.budget strong,.budget span,.budget small{display:block}.budget strong{color:#1474f8;font-size:34px}.budget span{color:#40506c;font-size:12px}.budget small{margin-top:10px;color:#75829a}.plan-values{flex:1;display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:0}.plan-values div{padding:12px;border:1px solid #e5eaf2;border-radius:9px}.plan-values dt{color:#8290a5;font-size:11px}.plan-values dd{margin:6px 0 0;color:#34435f;font-size:13px}.topics{width:100%;margin-top:16px}.topics span{margin-right:8px;color:#76839b;font-size:12px}.topics b,.topics em{display:inline-block;margin:3px;padding:4px 8px;border-radius:6px;color:#176fdc;background:#eaf3ff;font-size:12px;font-style:normal}.book-title{display:flex;align-items:flex-end;justify-content:space-between;margin-top:24px}.book-title span,.book-title strong,.book-title small{display:block}.book-title span,.book-title small{color:#7a879d;font-size:12px}.book-title strong{margin:5px 0;color:#11204a;font-size:22px}.book-title>b{color:#1474f8;font-size:28px}.progress{height:10px;margin-top:18px;overflow:hidden;border-radius:99px;background:#dce9fa}.progress i{display:block;height:100%;border-radius:inherit;background:#1a79f8}.book-stats{display:grid;grid-template-columns:repeat(3,1fr);margin-top:19px}.book-stats div{text-align:center;border-right:1px solid #e5eaf2}.book-stats div:last-child{border:0}.book-stats strong,.book-stats span{display:block}.book-stats strong{color:#14244c;font-size:20px}.book-stats span{margin-top:4px;color:#7b879d;font-size:11px}.estimate{margin:18px 0 0;padding:11px 13px;border-radius:8px;color:#5f6e88;background:#f7f9fc;font-size:12px}.estimate b{color:#1474f8}.empty-card{display:flex;min-height:160px;flex-direction:column;align-items:center;justify-content:center;color:#8a96a9}.empty-card strong{color:#59677f}.empty-card span{margin-top:8px;font-size:12px}.notebook-block{margin-top:18px}.notebook-block label{color:#6c7890;font-size:12px}.notebook-block select{height:34px;margin-left:5px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}.table-wrap{position:relative;min-height:160px;margin-top:16px;overflow:auto;border:1px solid #e2e7ef;border-radius:9px}table{width:100%;border-collapse:collapse}th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left;font-size:12px}th{color:#75819a;background:#f8faff}td{color:#53617a}td strong{color:#11204a;font-size:14px}.meaning{max-width:390px;white-space:normal}.tag{display:inline-block;padding:4px 8px;border-radius:6px;color:#176fdc;background:#eaf3ff}.empty{height:130px;text-align:center;color:#8994a7}.loading-mask{position:absolute;inset:40px 0 0;display:grid;place-items:center;color:#71809a;background:rgba(255,255,255,.86)}.pagination{display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}.pagination button:disabled{color:#bbc3cf}@media(max-width:1200px){.identity dl{grid-template-columns:1fr 1fr;gap:14px}.summary-grid,.detail-grid{grid-template-columns:1fr}.plan-content{flex-wrap:wrap}}
</style>
