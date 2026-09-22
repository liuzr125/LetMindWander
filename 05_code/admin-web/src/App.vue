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
const fallbackMenuItems = [
  { code:'overview', path:'/overview', icon:'⌂', name:'概览', description:'平台总体数据与待处理事项' },
  { code:'group_content', path:'', icon:'◇', name:'内容与来源', description:'词书、词条、批次与短文内容的来源与发布状态' },
  { code:'content', path:'/content', icon:'◇', name:'内容与来源', parentCode:'group_content', description:'内容来源、词条与文章明细' },
  { code:'words', path:'/words', icon:'Aa', name:'英语单词', parentCode:'group_content', description:'词书词条、学段与发音' },
  { code:'imports', path:'/vocabulary-imports', icon:'⇄', name:'词库批次', parentCode:'group_content', description:'词库批次导入与发布' },
  { code:'articles', path:'/articles', icon:'En', name:'英语短文', parentCode:'group_content', description:'每日英语短文与讲解' },
  { code:'group_users', path:'', icon:'●', name:'用户与学习', description:'用户账号状态与个人学习进度' },
  { code:'users', path:'/users', icon:'●', name:'用户状态', parentCode:'group_users', description:'用户状态、额度与学习进度' },
  { code:'feedback', path:'/feedback', icon:'✉', name:'用户反馈', parentCode:'group_users', description:'用户提交的反馈正文、详情与处理状态' },
  { code:'study_records', path:'/study-records', icon:'▤', name:'词书学习记录', parentCode:'group_users', description:'每个用户每本英语词书的学习记录、每日明细与已学词条' },
  { code:'group_ai', path:'', icon:'AI', name:'AI 与费用', description:'模型配置、月预算、用量日志与调用追溯' },
  { code:'ai', path:'/ai', icon:'AI', name:'AI 模型与费用', parentCode:'group_ai', description:'模型配置与月预算' },
  { code:'ai_audit', path:'/ai-audit', icon:'◉', name:'AI 使用追溯', parentCode:'group_ai', description:'按问题追溯模型调用' },
  { code:'ai_usage', path:'/ai-usage', icon:'▦', name:'AI 用量日志', parentCode:'group_ai', description:'每天每个用户的用量与费用' },
  { code:'group_system', path:'', icon:'⚙', name:'系统运维', description:'任务运行、系统参数与角色权限' },
  { code:'jobs', path:'/jobs', icon:'□', name:'任务与运行', parentCode:'group_system', description:'定时任务与运行结果' },
  { code:'parameters', path:'/parameters', icon:'⚙', name:'系统参数', parentCode:'group_system', description:'AI 额度等运行参数' },
  { code:'roles', path:'/roles', icon:'♙', name:'角色与权限', parentCode:'group_system', description:'角色、账号与菜单权限' }
]
function readItem(item, key) { const raw = item[key] ?? item[key.toUpperCase()] ?? null; return typeof raw === 'string' ? raw : raw }
const menuSource = computed(() => {
  const server = profile.value?.menuItems
  if (!Array.isArray(server) || !server.length) return fallbackMenuItems
  const codeById = new Map(server.map(item => [readItem(item, 'id'), readItem(item, 'code')]))
  return server.map(item => ({
    code: readItem(item, 'code'), name: readItem(item, 'name'), path: readItem(item, 'path') || '', icon: readItem(item, 'icon'),
    description: readItem(item, 'description'), parentCode: readItem(item, 'parentId') ? (codeById.get(readItem(item, 'parentId')) || null) : null
  }))
})
const visibleMenus = computed(() => menuSource.value.filter(item => profile.value?.menus?.includes(item.code)))
/** 一级菜单=分类，二级菜单=具体页面；父级不可见的子菜单仍单独展示，避免菜单丢失。 */
const navItems = computed(() => {
  const list = visibleMenus.value, groups = []
  list.filter(item => !item.parentCode).forEach(item => groups.push({ ...item, children: list.filter(child => child.parentCode === item.code) }))
  list.filter(item => item.parentCode && !groups.some(group => group.code === item.parentCode)).forEach(item => groups.push({ ...item, children: [] }))
  return groups
})
const openGroups = ref({})
const activeGroup = computed(() => (navItems.value.find(group => group.children.some(child => child.path === route.path)) || {}).code || '')
function groupOpen(group) { return openGroups.value[group.code] === undefined ? group.code === activeGroup.value : openGroups.value[group.code] }
function toggleGroup(group) { openGroups.value = { ...openGroups.value, [group.code]: !groupOpen(group) } }
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
        <template v-for="group in navItems" :key="group.code">
          <RouterLink v-if="!group.children.length" class="nav-link" :to="group.path"><b>{{ group.icon }}</b><span>{{ group.name }}</span></RouterLink>
          <div v-else class="nav-group">
            <button type="button" class="nav-group-head" @click="toggleGroup(group)">
              <b>{{ group.icon }}</b><span>{{ group.name }}</span><i class="nav-chevron">{{ groupOpen(group) ? '⌃' : '⌄' }}</i>
            </button>
            <div v-show="groupOpen(group)" class="nav-children">
              <small v-if="group.description" class="nav-group-desc">{{ group.description }}</small>
              <RouterLink v-for="child in group.children" :key="child.code" class="nav-link nav-child" :to="child.path" :title="child.description || child.name">
                <span>{{ child.name }}</span>
              </RouterLink>
            </div>
          </div>
        </template>
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
