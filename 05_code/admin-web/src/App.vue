<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getAdminToken, setAdminToken } from './services/request.js'

const route = useRoute()
const now = ref(new Date())
const showAccess = ref(!getAdminToken())
const tokenInput = ref(getAdminToken())
let timer

const pageTitle = computed(() => route.meta.title || '管理端')
const timeText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hour12: false
}).format(now.value).replaceAll('/', '-'))

function saveAccess() {
  if (!tokenInput.value.trim()) return
  setAdminToken(tokenInput.value.trim())
  showAccess.value = false
  window.dispatchEvent(new CustomEvent('admin-token-updated'))
}

function openAccess() {
  tokenInput.value = getAdminToken()
  showAccess.value = true
}

onMounted(() => {
  timer = window.setInterval(() => { now.value = new Date() }, 30000)
  window.addEventListener('admin-unauthorized', openAccess)
})
onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.removeEventListener('admin-unauthorized', openAccess)
})
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-logo"><i></i></div>
        <div><strong>脑袋开小灶</strong><span>管理端</span></div>
      </div>
      <nav class="side-nav">
        <RouterLink to="/overview"><b>⌂</b><span>概览</span></RouterLink>
        <RouterLink to="/content"><b>◇</b><span>内容与来源</span></RouterLink>
        <RouterLink to="/users"><b>●</b><span>用户状态</span></RouterLink>
        <RouterLink to="/jobs"><b>□</b><span>任务与运行</span></RouterLink>
      </nav>
      <button class="admin-card" @click="openAccess">
        <span class="admin-avatar">●</span>
        <span><strong>管理员</strong><small>访问凭证已配置</small></span>
        <b>›</b>
      </button>
    </aside>
    <main class="main-column">
      <header class="topbar">
        <span class="crumb">{{ pageTitle }}</span>
        <div class="topbar-meta"><span>◷</span><span>北京时间 {{ timeText }}</span><i></i><span class="mini-avatar">●</span><strong>管理员</strong><button @click="openAccess">⌄</button></div>
      </header>
      <div class="page-content"><RouterView /></div>
      <footer>脑袋开小灶 · 管理端</footer>
    </main>
  </div>

  <div v-if="showAccess" class="modal-mask">
    <form class="access-modal" @submit.prevent="saveAccess">
      <div class="modal-icon">🔐</div>
      <h2>管理员访问验证</h2>
      <p>请输入后端配置的管理端访问令牌。令牌仅保存在当前浏览器。</p>
      <label>访问令牌</label>
      <input v-model="tokenInput" type="password" autocomplete="current-password" placeholder="本地默认 dev-admin-token" autofocus />
      <button class="primary-button" type="submit">进入管理端</button>
    </form>
  </div>
</template>
