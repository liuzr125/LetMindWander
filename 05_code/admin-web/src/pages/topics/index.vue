<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../../services/request.js'
const items=ref([]), loading=ref(false), error=ref(''), message=ref(''), keyword=ref('')
const dialogOpen=ref(false), editingId=ref(''), name=ref(''), saving=ref(false), dialogError=ref(''), deletingId=ref('')
const typeFilter=ref('technical'), topicType=ref('technical')
const filtered=computed(()=>items.value.filter(item=>(!typeFilter.value || item.topicType===typeFilter.value) && item.name.toLowerCase().includes(keyword.value.trim().toLowerCase())))
async function load(){loading.value=true;error.value='';try{items.value=await request('/admin/topics')}catch(e){error.value=e.message}finally{loading.value=false}}
function edit(item){editingId.value=item?.id||'';name.value=item?.name||'';topicType.value=item?.topicType||typeFilter.value||'technical';dialogError.value='';dialogOpen.value=true}
function close(){if(!saving.value)dialogOpen.value=false}
async function save(){
  if(saving.value)return
  if(!name.value.trim()||name.value.trim().length>50){dialogError.value='请输入 1 至 50 个字符的主题名称';return}
  saving.value=true;dialogError.value=''
  try{const result=await request('/admin/topics'+(editingId.value?'/'+encodeURIComponent(editingId.value):''),{method:editingId.value?'PUT':'POST',body:JSON.stringify({name:name.value.trim(),topicType:topicType.value}),silentError:true});dialogOpen.value=false;message.value=result.message;await load()}
  catch(e){dialogError.value=e.message||'保存失败'}finally{saving.value=false}
}
async function remove(item){
  if(deletingId.value||!window.confirm(`确认删除主题“${item.name}”？将标记为已删除并从页面隐藏，保留主题记录、所有关联及内容，不会物理删除。`))return
  deletingId.value=item.id;message.value=''
  try{const result=await request('/admin/topics/'+encodeURIComponent(item.id),{method:'DELETE'});message.value=result.message;await load()}catch(e){error.value=e.message}finally{deletingId.value=''}
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>主题管理</h1><p>技术知识主题与词书标签分开管理；关联数量仅统计仍存在的内容，不影响词书成员。</p></div><div class="actions"><button class="outline" :disabled="loading" @click="load">刷新</button><button class="primary" @click="edit(null)">＋ 新增主题</button></div></div>
    <p v-if="message" class="success" role="status">{{message}}</p><p v-if="error" class="error" role="alert">{{error}}</p>
    <div class="panel topic-panel"><div class="toolbar"><select v-model="typeFilter" aria-label="主题类型"><option value="technical">技术知识主题</option><option value="vocabulary">词书标签</option><option value="">全部类型</option></select><input v-model="keyword" placeholder="搜索主题名称" maxlength="50" aria-label="搜索主题名称"/><span>共 {{filtered.length}} 个主题</span></div>
      <div class="table-wrap"><table><thead><tr><th>主题名称</th><th>类型</th><th>已发布技术知识</th><th>关联内容</th><th>学习计划</th><th>操作</th></tr></thead><tbody>
        <tr v-if="loading"><td colspan="6" class="empty">正在读取主题…</td></tr>
        <template v-else><tr v-for="item in filtered" :key="item.id"><td><strong>{{item.name}}</strong></td><td>{{item.topicType==='vocabulary'?'词书标签':'技术知识'}}</td><td>{{item.publishedCount}} 条</td><td>{{item.contentCount}} 条</td><td>{{item.planCount}} 个</td><td class="actions"><button class="link" @click="edit(item)">编辑</button><button class="link danger" :disabled="!!deletingId || item.publishedCount>0 || item.planCount>0" :title="item.publishedCount>0 || item.planCount>0 ? '有已发布技术知识或学习计划引用，不能删除' : '删除未使用主题'" @click="remove(item)">{{deletingId===item.id?'删除中…':'删除'}}</button></td></tr><tr v-if="!filtered.length"><td colspan="6" class="empty">暂无符合条件的主题</td></tr></template>
      </tbody></table></div><p class="hint">只管理系统主题；已发布技术知识为 0 且没有学习计划引用的主题可删除；仅标记删除，保留主题记录、关联及内容。</p>
    </div>
    <div v-if="dialogOpen" class="mask" @click.self="close" @keydown.esc="close"><form class="dialog" role="dialog" aria-modal="true" aria-labelledby="topic-dialog-title" @submit.prevent="save"><header><h2 id="topic-dialog-title">{{editingId?'编辑主题':'新增主题'}}</h2><button type="button" :disabled="saving" aria-label="关闭" @click="close">×</button></header><label>主题名称<input v-model="name" required maxlength="50" :disabled="saving" placeholder="例如：云原生"/></label><label>类型<select v-model="topicType" :disabled="saving"><option value="technical">技术知识主题</option><option value="vocabulary">词书标签</option></select></label><p class="hint">词书标签不参与技术知识达标统计；有非单词内容或学习计划引用的主题不能改为词书标签。</p><p v-if="dialogError" class="error" role="alert">{{dialogError}}</p><footer class="dialog-actions"><button class="outline" type="button" :disabled="saving" @click="close">取消</button><button class="primary" type="submit" :disabled="saving">{{saving?'保存中…':'保存'}}</button></footer></form></div>
  </section>
</template>

<style scoped>
.dialog .dialog-actions{height:auto;margin-top:24px;padding:0;gap:10px;line-height:normal}
.dialog-actions button{display:inline-flex;align-items:center;justify-content:center;box-sizing:border-box;height:38px;min-width:80px;padding:0 18px;border-radius:8px;font-size:14px;font-weight:500;line-height:1;white-space:nowrap;transition:background-color .15s,border-color .15s}
.dialog-actions .outline{border-color:#d7dfeb;color:#53617a;background:#fff}
.dialog-actions .outline:hover:not(:disabled){border-color:#b7c6dc;background:#f6f8fc}
.dialog-actions .primary:hover:not(:disabled){border-color:#0866ef;background:#0866ef}
.dialog-actions button:focus-visible{outline:2px solid #1677ff;outline-offset:3px}
.actions{display:flex;gap:12px;align-items:center}.primary,.outline{padding:10px 18px;border-radius:9px;border:1px solid #cadcf7;font:inherit;cursor:pointer}.primary{background:#1677ff;color:white;border-color:#1677ff}.outline{background:white;color:#1677ff}button:disabled{opacity:.45;cursor:not-allowed}.topic-panel{padding:24px}.toolbar select,.dialog select{padding:12px;border:1px solid #d6e1f3;border-radius:9px;font:inherit;background:white}.toolbar{gap:16px;flex-wrap:wrap;display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;color:#7b89a5}input{padding:12px;border:1px solid #d6e1f3;border-radius:9px;font:inherit;box-sizing:border-box}.toolbar input{width:min(400px,65%)}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:18px 14px;border-bottom:1px solid #e7edf6}th{color:#7a89a4;background:#f7faff}.link{border:0;background:none;color:#1677ff;font:inherit;cursor:pointer}.danger,.error{color:#df4353}.success{color:#08976e}.hint,.empty{color:#8592a9;font-size:14px}.empty{text-align:center}.mask{position:fixed;inset:0;background:#18294466;display:flex;align-items:center;justify-content:center;z-index:90;padding:20px}.dialog{background:white;border-radius:18px;padding:28px;width:min(460px,100%);box-shadow:0 18px 70px #152b5533}.dialog header{display:flex;justify-content:space-between;align-items:center}.dialog header button{border:0;background:none;font-size:26px;color:#71809a;cursor:pointer}.dialog label{display:grid;gap:12px;margin:24px 0}.dialog input{width:100%}.dialog footer{display:flex;justify-content:flex-end;gap:12px}
</style>
