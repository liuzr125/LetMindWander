<script setup>
import { onMounted, ref, watch } from 'vue'
import { request } from '../../services/request.js'

const rows = ref([])
const loading = ref(false)
const keywordInput = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = ref(0)
const error = ref('')
const message = ref('')
const dialogOpen = ref(false)
const editing = ref(false)
const form = ref(blankForm())

function blankForm() { return { id:'', paramKey:'', paramValue:'', isSecret:false, description:'', state:'active' } }
function flash(text) { message.value=text; error.value=''; window.setTimeout(()=>{if(message.value===text)message.value=''},3600) }
function fail(e) { error.value=e.message||'操作失败'; message.value='' }
async function load() { loading.value=true; error.value=''; const params=new URLSearchParams({page:String(page.value),pageSize:String(pageSize.value)});if(keyword.value)params.set('keyword',keyword.value);try { const result=await request(`/admin/parameters?${params}`);rows.value=result.items||[];total.value=result.total||0;totalPages.value=result.totalPages||0;page.value=result.page||1 } catch(e) { fail(e) } finally { loading.value=false } }
function search() { keyword.value=keywordInput.value.trim();page.value=1;load() }
function clearSearch() { keywordInput.value='';keyword.value='';page.value=1;load() }
function turn(target) { if(target<1||target>totalPages.value||target===page.value)return;page.value=target;load() }
function openCreate() { editing.value=false; form.value=blankForm(); dialogOpen.value=true }
function openEdit(row) { editing.value=true; form.value={ id:row.id, paramKey:row.paramKey, paramValue:row.isSecret?'':(row.paramValue||''), isSecret:!!row.isSecret, description:row.description||'', state:row.state||'active' }; dialogOpen.value=true }
async function submit() { const body={ paramKey:form.value.paramKey.trim(), paramValue:form.value.paramValue, isSecret:form.value.isSecret, description:form.value.description.trim(), state:form.value.state }; try { await request(editing.value?`/admin/parameters/${encodeURIComponent(form.value.id)}`:'/admin/parameters',{ method:editing.value?'PUT':'POST', body:JSON.stringify(body) }); dialogOpen.value=false;if(!editing.value)page.value=1;await load(); flash(editing.value?'系统参数已更新':'系统参数已新增') } catch(e) { fail(e) } }
async function remove(row) { if(!window.confirm(`确定软删除参数“${row.paramKey}”吗？运行中的服务将不再读取该参数。`)) return; try { await request(`/admin/parameters/${encodeURIComponent(row.id)}`,{method:'DELETE'});if(rows.value.length===1&&page.value>1)page.value-=1;await load(); flash('参数已软删除，可用相同参数键重新新增或由配置流程恢复') } catch(e) { fail(e) } }
function stateText(state) { return state==='active'?'启用':'停用' }

watch(pageSize,()=>{page.value=1;if(!loading.value)load()})
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>系统参数</h1><p>管理服务运行参数。敏感值以加密形式保存，列表不会显示原文；删除只做软删除标记。</p></div><div class="heading-actions"><button @click="load">刷新</button><button class="primary" @click="openCreate">新增参数</button></div></div>
    <p v-if="message" class="notice success">{{ message }}</p><p v-if="error" class="notice">{{ error }}</p>
    <div class="panel"><div class="filter"><form @submit.prevent="search"><input v-model="keywordInput" maxlength="100" placeholder="搜索参数键或说明" /><button class="primary">搜索</button><button v-if="keyword" type="button" @click="clearSearch">清除</button></form><label>每页 <select v-model.number="pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select> 条</label></div><div class="table-wrap"><table><thead><tr><th>参数键</th><th>参数值</th><th>类型</th><th>说明</th><th>状态</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><code>{{ row.paramKey }}</code></td><td class="value">{{ row.valueDisplay || '—' }}</td><td><span :class="['pill',row.isSecret?'secret':'plain']">{{ row.isSecret?'敏感参数':'普通参数' }}</span></td><td>{{ row.description }}</td><td><span :class="['pill',row.state==='active'?'active':'inactive']">{{ stateText(row.state) }}</span></td><td>v{{ row.versionNo }}</td><td><button class="link" @click="openEdit(row)">编辑</button><button class="danger-link" @click="remove(row)">删除</button></td></tr><tr v-if="!loading&&!rows.length"><td colspan="7" class="empty">没有符合条件的系统参数</td></tr><tr v-if="loading"><td colspan="7" class="empty">加载中…</td></tr></tbody></table></div><div class="pagination"><span>{{ total ? `${(page-1)*pageSize+1}–${Math.min(total,page*pageSize)} / ${total} 条` : '0 条' }}</span><button :disabled="page<=1" @click="turn(page-1)">‹</button><span>第 {{ page }} / {{ totalPages || 1 }} 页</span><button :disabled="page>=totalPages" @click="turn(page+1)">›</button></div></div>
  </section>
  <div v-if="dialogOpen" class="modal-mask" @click.self="dialogOpen=false"><form class="dialog" @submit.prevent="submit"><h2>{{ editing?'编辑系统参数':'新增系统参数' }}</h2><p v-if="editing&&form.isSecret" class="hint">敏感参数已加密保存；不填写参数值将保留现有值。</p><label>参数键<input v-model="form.paramKey" :disabled="editing" maxlength="100" placeholder="例如 tts.aliyun.voice" required /></label><label>参数值<input v-model="form.paramValue" :type="form.isSecret?'password':'text'" maxlength="4096" :placeholder="editing&&form.isSecret?'留空表示不修改':'请输入参数值'" :required="!editing || !form.isSecret" /></label><label class="check"><input v-model="form.isSecret" type="checkbox" />敏感参数（加密保存，不能回显）</label><label>参数说明<input v-model="form.description" maxlength="200" required /></label><label>状态<select v-model="form.state"><option value="active">启用</option><option value="inactive">停用</option></select></label><div class="form-actions"><button type="button" @click="dialogOpen=false">取消</button><button class="primary" type="submit">{{ editing?'保存修改':'新增参数' }}</button></div></form></div>
</template>

<style scoped>
.page-heading,.heading-actions,.filter,.filter form,.form-actions,.pagination{display:flex;align-items:center}.page-heading{justify-content:space-between;gap:16px}.page-heading h1{margin:0;color:#14234a}.page-heading p,.hint{margin:7px 0 0;color:#74819a;font-size:13px}.heading-actions,.filter form,.form-actions{gap:9px}.heading-actions button,.filter button,.form-actions button{height:39px;padding:0 15px;border:1px solid #c7d6ea;border-radius:8px;color:#43516b;background:#fff}.primary{border-color:#0d6ff5!important;color:#fff!important;background:#0d6ff5!important}.panel{margin-top:22px;padding:20px;border:1px solid #dce5f2;border-radius:14px;background:#fff}.filter{justify-content:space-between;gap:18px;margin-bottom:16px}.filter input{width:min(420px,42vw);height:39px;padding:0 12px;border:1px solid #d5deeb;border-radius:8px;color:#1e315a}.filter label{color:#6c7890;font-size:12px;white-space:nowrap}.filter select{height:34px;border:1px solid #d7dfeb;border-radius:7px;background:#fff}.table-wrap{overflow:auto;border:1px solid #e2e7ef;border-radius:9px}table{width:100%;border-collapse:collapse;font-size:13px;white-space:nowrap}th{height:42px;padding:0 12px;color:#68758e;background:#f8faff;text-align:left}td{height:52px;padding:0 12px;border-top:1px solid #e9edf3;color:#52617c}.value{max-width:360px;overflow:hidden;text-overflow:ellipsis}code{color:#153264}.pill{display:inline-block;padding:4px 8px;border-radius:99px;font-size:12px}.secret{color:#8b5b15;background:#fff5e5}.plain{color:#346183;background:#ebf5ff}.active{color:#087951;background:#e7f8f1}.inactive{color:#6e7787;background:#eef1f5}.link,.danger-link{border:0;background:transparent}.link{color:#0d6ff5}.danger-link{margin-left:10px;color:#c13f4a}.empty{text-align:center;color:#8994a7}.pagination{justify-content:flex-end;gap:10px;margin-top:14px;color:#6f7c94;font-size:12px}.pagination button{width:32px;height:30px;border:1px solid #d7dfeb;border-radius:7px;color:#0d6ff5;background:#fff}.pagination button:disabled{color:#bbc3cf}.notice{margin:18px 0;padding:12px 15px;border:1px solid #efc7cb;border-radius:9px;color:#b83c48;background:#fff4f5}.notice.success{border-color:#bfe7d4;color:#087951;background:#effbf5}.modal-mask{position:fixed;inset:0;z-index:10;display:grid;place-items:center;background:rgba(11,25,54,.44)}.dialog{width:min(540px,90vw);padding:25px;border-radius:14px;background:#fff;box-shadow:0 22px 70px rgba(15,35,75,.28)}.dialog h2{margin:0;color:#13234b}.dialog label{display:grid;gap:6px;margin-top:14px;color:#52617c;font-size:13px}.dialog input,.dialog select{box-sizing:border-box;width:100%;min-height:39px;padding:8px 10px;border:1px solid #d5deeb;border-radius:8px;color:#1e315a;background:#fff}.dialog input:disabled{color:#8894a8;background:#f6f8fb}.dialog .check{display:flex;align-items:center;gap:8px}.dialog .check input{width:auto;min-height:auto}.form-actions{justify-content:flex-end;margin-top:23px}@media(max-width:650px){.page-heading{align-items:flex-start;flex-direction:column}.filter{align-items:stretch;flex-direction:column}.filter form{align-items:stretch;flex-direction:column}.filter input{width:auto}}
</style>
