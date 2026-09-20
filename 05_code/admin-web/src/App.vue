<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import brandLogo from './assets/brand-logo.png'
import { getAdminToken, request, setAdminToken } from './services/request.js'
import { closeNotification, notification } from './services/notification.js'

const route = useRoute()
const now = ref(new Date())
const showAccess = ref(true)
const loginForm = ref({ username: 'superadmin', password: '' })
const loginError = ref('')
const loggingIn = ref(false)
const profile = ref(null)
const menuDefinitions = [
  { code:'overview', path:'/overview', icon:'⌂', name:'概览' }, { code:'content', path:'/content', icon:'◇', name:'内容与来源' },
  { code:'words', path:'/words', icon:'Aa', name:'英语单词' }, { code:'imports', path:'/vocabulary-imports', icon:'⇄', name:'词库批次' },
  { code:'articles', path:'/articles', icon:'En', name:'英语短文' }, { code:'users', path:'/users', icon:'●', name:'用户状态' },
  { code:'jobs', path:'/jobs', icon:'□', name:'任务与运行' }, { code:'ai', path:'/ai', icon:'AI', name:'AI 模型与费用' }, { code:'ai_audit', path:'/ai-audit', icon:'◉', name:'AI 使用追溯' },
  { code:'parameters', path:'/parameters', icon:'⚙', name:'系统参数' }, { code:'roles', path:'/roles', icon:'♙', name:'角色与权限' }
]
const visibleMenus = computed(() => menuDefinitions.filter(item => profile.value?.menus?.includes(item.code)))
let timer

const pageTitle = computed(() => route.meta.title || '管理端')
const timeText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hour12: false
}).format(now.value).replaceAll('/', '-'))

async function saveAccess() {
  loginError.value = ''; loggingIn.value = true
  try {
    const result = await request('/admin/auth/login', { method:'POST', body:JSON.stringify(loginForm.value), silentError:true })
    setAdminToken(result.token); profile.value = result; showAccess.value = false
    window.dispatchEvent(new CustomEvent('admin-token-updated'))
  } catch (error) { loginError.value = error.message || '登录失败' }
  finally { loggingIn.value = false }
}

function openAccess() {
  setAdminToken(''); profile.value = null; loginForm.value.password = ''; loginError.value = ''
  showAccess.value = true
}
async function restoreSession(){
  if(!getAdminToken()){showAccess.value=true;return}
  try { profile.value=await request('/admin/auth/me',{silentError:true}); showAccess.value=false }
  catch (_) { setAdminToken(''); showAccess.value=true }
}
async function logout(){try{await request('/admin/auth/logout',{method:'POST',silentError:true})}catch(_){/* local clear still protects this browser */}openAccess()}

onMounted(() => {
  timer = window.setInterval(() => { now.value = new Date() }, 30000)
  window.addEventListener('admin-unauthorized', openAccess)
  restoreSession()
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
        <img class="brand-logo" :src="brandLogo" alt="脑袋开小灶" />
        <div><strong>脑袋开小灶</strong><span>管理端</span></div>
      </div>
      <nav class="side-nav">
        <RouterLink v-for="item in visibleMenus" :key="item.code" :to="item.path"><b>{{ item.icon }}</b><span>{{ item.name }}</span></RouterLink>
      </nav>
      <div class="admin-card">
        <span class="admin-avatar">●</span>
        <span><strong>{{ profile?.username || '未登录' }}</strong><small>{{ profile?.roleName || '请先登录' }}</small></span>
      </div>
    </aside>
    <main class="main-column">
      <header class="topbar">
        <span class="crumb">{{ pageTitle }}</span>
        <div class="topbar-meta"><span>◷</span><span>北京时间 {{ timeText }}</span><i></i><span class="mini-avatar">●</span><strong>{{ profile?.username || '未登录' }}</strong><button type="button" class="logout-button" title="退出登录" @click="logout">退出</button></div>
      </header>
      <div class="page-content"><RouterView /></div>
      <footer>脑袋开小灶 · 管理端</footer>
    </main>
  </div>

  <div v-if="showAccess" class="modal-mask">
    <form class="access-modal" @submit.prevent="saveAccess">
      <div class="modal-icon">🔐</div>
      <h2>管理端登录</h2>
      <p>输入管理员账号和密码。登录会话仅保存于当前浏览器，并在 8 小时后自动失效。</p>
      <label>账号</label>
      <input v-model.trim="loginForm.username" autocomplete="username" maxlength="40" autofocus />
      <label>密码</label>
      <input v-model="loginForm.password" type="password" autocomplete="current-password" maxlength="128" placeholder="请输入密码" />
      <p v-if="loginError" class="login-error">{{ loginError }}</p>
      <button class="primary-button" :disabled="loggingIn" type="submit">{{ loggingIn ? '登录中…' : '进入管理端' }}</button>
    </form>
  </div>
  <div v-if="notification.open" class="modal-mask notification-mask" @click.self="closeNotification">
    <div class="notification-dialog" role="alertdialog" aria-modal="true" aria-labelledby="notification-title">
      <div class="notification-icon">!</div>
      <h2 id="notification-title">{{ notification.title }}</h2>
      <p>{{ notification.message }}</p>
      <div v-if="notification.help" class="notification-help">{{ notification.help }}</div>
      <small v-if="notification.code">诊断码：<code>{{ notification.code }}</code></small>
      <button type="button" class="primary-button" @click="closeNotification">我知道了</button>
    </div>
  </div>
</template>
