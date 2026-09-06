<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { request } from '../../services/request.js'

const loading = ref(false)
const message = ref('')
const status = ref('all')
const page = ref(1)
const total = ref(0)
const items = ref([])
const stats = reactive({ invitedLimit: 0, invitedUsed: 0, remaining: 0, availableCodes: 0, redeemedCodes: 0 })
const createOpen = ref(false)
const generatedOpen = ref(false)
const createdCodes = ref([])
const form = reactive({ count: 1, expiresInDays: 7 })
const limitDraft = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
const hasItems = computed(() => items.value.length > 0)

function fmt(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hour12:false }).format(new Date(value)).replace('/', '-')
}

function shortId(id) { return id ? `记录 · ${id.slice(-8).toUpperCase()}` : '—' }
function statusText(value) { return ({ available:'待兑换', redeemed:'已兑换', revoked:'已撤销', expired:'已过期' })[value] || value }

async function load() {
  loading.value = true
  message.value = ''
  try {
    const [list, summary] = await Promise.all([
      request(`/admin/invites?status=${status.value}&page=${page.value}&size=10`),
      request('/admin/invite-stats')
    ])
    items.value = list.content || []
    total.value = list.total || 0
    Object.assign(stats, summary)
    limitDraft.value = String(summary.invitedLimit ?? '')
  } catch (error) {
    message.value = error.message
  } finally { loading.value = false }
}

async function createInvites() {
  loading.value = true
  try {
    const result = await request('/admin/invites', { method:'POST', body:JSON.stringify(form) })
    createdCodes.value = result.codes || []
    createOpen.value = false
    generatedOpen.value = true
    await load()
  } catch (error) { message.value = error.message; loading.value = false }
}

async function revoke(item) {
  if (!window.confirm('确认撤销这条未使用的邀请码吗？撤销后无法恢复。')) return
  try { await request(`/admin/invites/${item.id}/revoke`, { method:'POST' }); await load() }
  catch (error) { message.value = error.message }
}

async function remove(item) {
  if (!window.confirm('确认删除这条邀请码记录吗？删除后无法恢复。')) return
  try { await request(`/admin/invites/${item.id}`, { method:'DELETE' }); await load() }
  catch (error) { message.value = error.message }
}

async function updateLimit() {
  const value = Number(limitDraft.value)
  if (!Number.isInteger(value) || value < 1) { message.value = '请输入有效的名额上限'; return }
  try { await request('/admin/admission', { method:'PUT', body:JSON.stringify({ invitedLimit:value }) }); await load() }
  catch (error) { message.value = error.message }
}

async function copy(text) {
  await navigator.clipboard.writeText(text)
  message.value = '邀请码已复制到剪贴板'
}

function filterChanged() { page.value = 1; load() }
function tokenUpdated() { load() }
onMounted(() => { window.addEventListener('admin-token-updated', tokenUpdated); load() })
onBeforeUnmount(() => window.removeEventListener('admin-token-updated', tokenUpdated))
</script>

<template>
  <section class="users-page">
    <div class="page-heading">
      <div><h1>用户状态</h1><p>管理试用资格、邀请码与准入名额。</p></div>
      <button class="refresh-button" :disabled="loading" @click="load">↻ 刷新</button>
    </div>

    <div class="tabs"><button>账号列表</button><button class="active">邀请码</button></div>
    <div class="notice-line"><span>ⓘ</span><span>邀请码明文仅在生成时显示一次，后续只保留安全摘要和状态。</span></div>

    <div class="stat-grid">
      <article class="panel stat-card blue"><span class="stat-icon">♙</span><div><small>试用名额</small><strong>{{ stats.invitedLimit }}<em> 位</em></strong><p>可按实际容量调整</p></div></article>
      <article class="panel stat-card green"><span class="stat-icon">●●●</span><div><small>好友已加入</small><strong>{{ stats.invitedUsed }}</strong><p>已占用准入名额</p></div></article>
      <article class="panel stat-card orange"><span class="stat-icon">＋</span><div><small>当前可邀请</small><strong>{{ stats.remaining }}</strong><p>可用邀请码 {{ stats.availableCodes }} 条</p></div></article>
    </div>

    <div v-if="message" class="message-line">{{ message }}<button @click="message=''">×</button></div>

    <div class="workspace-grid">
      <section class="panel invite-panel">
        <div class="panel-head">
          <div><h2>邀请码管理</h2><p>一次性使用 · 默认 7 天有效</p></div>
          <button class="primary-button create-button" @click="createOpen=true">⊕ 生成邀请码</button>
        </div>
        <div class="toolbar">
          <label>状态<select v-model="status" @change="filterChanged"><option value="all">全部</option><option value="available">待兑换</option><option value="redeemed">已兑换</option><option value="revoked">已撤销</option><option value="expired">已过期</option></select></label>
          <span>共 {{ total }} 条记录</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>安全记录</th><th>状态</th><th>创建时间</th><th>到期时间</th><th>兑换时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="loading && !hasItems"><td colspan="6" class="empty">正在读取邀请码…</td></tr>
              <tr v-else-if="!hasItems"><td colspan="6" class="empty">暂无邀请码，点击右上角生成</td></tr>
              <tr v-for="item in items" :key="item.id">
                <td class="record-id">{{ shortId(item.id) }}</td><td><span :class="['status-pill',item.status]">{{ statusText(item.status) }}</span></td>
                <td>{{ fmt(item.createdAt) }}</td><td>{{ fmt(item.expiresAt) }}</td><td>{{ fmt(item.redeemedAt) }}</td>
                <td><button class="link-button danger" :disabled="item.status !== 'available'" @click="revoke(item)">撤销</button><button class="link-button danger" style="margin-left:10px" :disabled="item.status === 'redeemed'" @click="remove(item)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination"><button :disabled="page<=1" @click="page--;load()">‹</button><span>{{ page }} / {{ totalPages }}</span><button :disabled="page>=totalPages" @click="page++;load()">›</button></div>
      </section>

      <aside class="panel quota-panel">
        <h2>准入名额</h2><p>调整后立即用于新注册校验，不能低于已占用名额。</p>
        <div class="quota-ring" :style="{'--progress': `${Math.min(stats.invitedUsed / Math.max(stats.invitedLimit,1) * 360,360)}deg`}"><div><strong>{{ stats.remaining }}</strong><span>剩余</span></div></div>
        <label>受邀用户上限<input v-model="limitDraft" type="number" min="1" max="65535" /></label>
        <button class="outline-button" @click="updateLimit">保存名额设置</button>
        <div class="quota-detail"><span>已加入 <b>{{ stats.invitedUsed }}</b></span><span>可用邀请码 <b>{{ stats.availableCodes }}</b></span><span>已兑换 <b>{{ stats.redeemedCodes }}</b></span></div>
      </aside>
    </div>
  </section>

  <div v-if="createOpen" class="modal-mask">
    <form class="dialog" @submit.prevent="createInvites"><h2>生成邀请码</h2><p>邀请码为一次性凭证，明文只展示一次。</p><label>生成数量<input v-model.number="form.count" type="number" min="1" max="100" /></label><label>有效天数<input v-model.number="form.expiresInDays" type="number" min="1" max="30" /></label><div class="dialog-actions"><button type="button" class="outline-button" @click="createOpen=false">取消</button><button class="primary-button" type="submit">确认生成</button></div></form>
  </div>

  <div v-if="generatedOpen" class="modal-mask">
    <div class="dialog generated-dialog"><div class="success-icon">✓</div><h2>邀请码已生成</h2><p>请现在复制并安全发送，关闭后无法再次查看明文。</p><div class="code-list"><div v-for="item in createdCodes" :key="item.id"><code>{{ item.code }}</code><button @click="copy(item.code)">复制</button></div></div><button class="primary-button done-button" @click="generatedOpen=false;createdCodes=[]">我已保存</button></div>
  </div>
</template>

<style scoped>
.refresh-button { height:40px; padding:0 17px; border:1px solid #bad3fb; border-radius:8px; background:#fff; color:#0b6ff5; }.tabs { display:flex; gap:32px; height:53px; align-items:flex-end; margin-top:10px; border-bottom:1px solid #e4e9f1; }.tabs button { height:43px; padding:0 4px; border:0; border-bottom:3px solid transparent; background:transparent; color:#73809a; font-size:15px; }.tabs button.active { border-color:#1878fa; color:#1474f7; font-weight:650; }.notice-line { height:44px; display:flex; align-items:center; gap:10px; margin-top:17px; padding:0 16px; border:1px solid #dce5f1; border-radius:10px; background:rgba(255,255,255,.72); color:#66748f; font-size:13px; }
.stat-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:18px; margin-top:18px; }.stat-card { min-height:123px; display:flex; align-items:center; gap:20px; padding:20px 24px; }.stat-icon { width:62px; height:62px; display:grid; place-items:center; border-radius:50%; font-size:22px; }.stat-card.blue .stat-icon { background:#e9f2ff; color:#1474f8; }.stat-card.green .stat-icon { background:#e8f8f2; color:#16a777; font-size:11px; }.stat-card.orange .stat-icon { background:#fff3e4; color:#f49715; font-size:30px; }.stat-card small { display:block; color:#52607d; font-size:13px; }.stat-card strong { display:block; margin-top:5px; color:#1170f6; font-size:32px; line-height:1; }.stat-card.green strong { color:#0da779; }.stat-card.orange strong { color:#f39410; }.stat-card em { font-size:13px; font-style:normal; }.stat-card p { margin:8px 0 0; color:#7a879e; font-size:12px; }
.message-line { display:flex; justify-content:space-between; margin-top:14px; padding:10px 14px; border:1px solid #cfe1ff; border-radius:9px; background:#eff6ff; color:#1768d8; font-size:13px; }.message-line button { border:0; background:transparent; color:inherit; }
.workspace-grid { display:grid; grid-template-columns:minmax(650px,1fr) 310px; gap:18px; margin-top:18px; }.invite-panel { min-width:0; padding:18px 16px 14px; }.panel-head { display:flex; align-items:center; justify-content:space-between; padding:0 2px 16px; }.panel h2 { margin:0; font-size:18px; }.panel-head p,.quota-panel>p { margin:5px 0 0; color:#7b879d; font-size:12px; }.create-button { width:132px; }.toolbar { height:48px; display:flex; align-items:center; justify-content:space-between; padding:0 4px; border-top:1px solid #edf0f5; color:#7b879b; font-size:12px; }.toolbar label { color:#56647e; }.toolbar select { margin-left:8px; padding:5px 28px 5px 10px; border:1px solid #dce3ee; border-radius:7px; background:#fff; color:#26334f; }
.table-wrap { overflow:auto; border:1px solid #e2e7ef; border-radius:9px; } table { width:100%; border-collapse:collapse; font-size:12px; white-space:nowrap; } th { height:40px; padding:0 12px; background:#f8faff; color:#68758f; font-weight:600; text-align:left; } td { height:45px; padding:0 12px; border-top:1px solid #e8ecf2; color:#5e6d88; }.record-id { color:#253552; font-family:ui-monospace,SFMono-Regular,Menlo,monospace; }.status-pill { display:inline-flex; align-items:center; height:26px; padding:0 9px; border-radius:7px; background:#fff4dd; color:#ed8d00; font-weight:650; }.status-pill.redeemed { background:#e9f8f1; color:#0ca879; }.status-pill.revoked,.status-pill.expired { background:#f1f3f6; color:#68758d; }.link-button { border:0; background:transparent; color:#1474f8; }.link-button.danger { color:#ef4050; }.link-button:disabled { color:#c5cbd5; cursor:not-allowed; }.empty { height:160px; color:#9aa4b6; text-align:center; }.pagination { display:flex; align-items:center; justify-content:flex-end; gap:10px; padding-top:13px; color:#71809b; font-size:12px; }.pagination button { width:30px; height:28px; border:1px solid #dce3ee; border-radius:7px; background:#fff; color:#1674f7; }.pagination button:disabled { color:#bdc5d2; }
.quota-panel { padding:20px; }.quota-ring { --progress:0deg; width:132px; height:132px; display:grid; place-items:center; margin:23px auto; border-radius:50%; background:conic-gradient(#1a79f8 var(--progress),#eaf0f8 0); }.quota-ring::before { content:''; width:104px; height:104px; position:absolute; border-radius:50%; background:#fff; }.quota-ring div { z-index:1; text-align:center; }.quota-ring strong,.quota-ring span { display:block; }.quota-ring strong { color:#1674f7; font-size:30px; }.quota-ring span { margin-top:3px; color:#79869c; font-size:11px; }.quota-panel label { display:block; color:#4e5d79; font-size:12px; font-weight:650; }.quota-panel input { width:100%; height:39px; margin-top:8px; padding:0 11px; border:1px solid #dbe2ec; border-radius:8px; outline:none; }.outline-button { height:40px; border:1px solid #cfd9e7; border-radius:8px; background:#fff; color:#42516d; }.quota-panel>.outline-button { width:100%; margin-top:12px; color:#0e6ff5; border-color:#bcd4f8; }.quota-detail { display:grid; grid-template-columns:1fr 1fr; gap:9px; margin-top:18px; padding-top:16px; border-top:1px solid #edf0f5; }.quota-detail span { padding:10px; border-radius:8px; background:#f7f9fc; color:#7a879d; font-size:11px; }.quota-detail b { display:block; margin-top:4px; color:#263550; font-size:16px; }
.dialog { width:420px; padding:28px; border-radius:16px; background:#fff; box-shadow:0 30px 90px rgba(13,33,72,.22); }.dialog h2 { margin:0; font-size:22px; }.dialog>p { margin:8px 0 22px; color:#78849a; font-size:13px; line-height:1.6; }.dialog label { display:block; margin-top:14px; color:#4a5873; font-size:13px; font-weight:650; }.dialog input { width:100%; height:42px; margin-top:8px; padding:0 11px; border:1px solid #d8e0eb; border-radius:8px; }.dialog-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:24px; }.dialog-actions button { width:110px; }.generated-dialog { width:470px; }.success-icon { width:48px; height:48px; display:grid; place-items:center; border-radius:50%; background:#e8f8f1; color:#0bad78; font-size:24px; }.code-list { max-height:280px; overflow:auto; display:flex; flex-direction:column; gap:8px; }.code-list div { display:flex; align-items:center; padding:10px 12px; border:1px solid #dbe4ef; border-radius:9px; background:#f8faff; }.code-list code { flex:1; color:#0d55ba; font-size:16px; font-weight:700; letter-spacing:1px; }.code-list button { border:0; background:transparent; color:#1172f6; }.done-button { width:100%; margin-top:20px; }
@media (max-width:1280px) { .workspace-grid { grid-template-columns:1fr; }.quota-panel { display:grid; grid-template-columns:1fr 160px 240px; align-items:center; gap:10px 20px; }.quota-panel>p { grid-column:1 }.quota-ring { grid-column:2; grid-row:1/5; }.quota-detail { grid-column:3; grid-row:1/5; }.quota-panel>.outline-button { grid-column:1; } }
</style>
