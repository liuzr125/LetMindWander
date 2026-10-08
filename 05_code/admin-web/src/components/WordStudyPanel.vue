<script setup>
import { onMounted, ref, watch } from 'vue'
import { request } from '../services/request.js'
const props = defineProps({ roundId: String, showMode: Boolean })
const mode = ref('fixed'), saving = ref(false), error = ref(''), result = ref({ items: [], total: 0 }), page = ref(1)
function value(row, key) { return row[key] ?? row[key.toUpperCase()] }
function label(v) { return ({ fixed: '固定间隔', flexible: '灵活间隔', remember: '记得', fuzzy: '模糊', unclear: '不认识', new: '新学 / 巩固', review: '到期复习' })[v] || v || '—' }
function time(v) { return v ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'short' }).format(new Date(v)) : '—' }
async function load() {
 try { const q = new URLSearchParams({ page: page.value, pageSize: 20 }); if (props.roundId) q.set('roundId', props.roundId); result.value = await request(`/admin/study-records/word-study/events?${q}`); error.value = '' } catch (e) { error.value = e.message }
}
async function save(event) {
 const next = event.target.value; saving.value = true
 try { const saved = await request('/admin/study-records/word-study/mode', { method: 'PUT', body: JSON.stringify({ intervalMode: next }) }); mode.value = saved.intervalMode; error.value = '' } catch (e) { error.value = e.message; event.target.value = mode.value } finally { saving.value = false }
}
onMounted(async () => { if (props.showMode) { try { mode.value = (await request('/admin/study-records/word-study/mode')).intervalMode } catch (e) { error.value = e.message } } load() })
watch(() => props.roundId, () => { page.value = 1; load() })
</script>
<template>
 <div class="panel study-events">
  <div v-if="showMode" class="mode-row"><strong>全局单词复习模式</strong><select :value="mode" :disabled="saving" @change="save"><option value="fixed">固定间隔</option><option value="flexible">灵活间隔</option></select><p>应用于所有用户。保留当前到期日和巩固进度，从下一次完成反馈开始使用；修改会记录管理员审计。</p></div>
  <h2>单词学习与复习反馈详情</h2><p v-if="error" role="alert">{{error}}</p>
  <div class="table-wrap"><table><thead><tr><th>词 / 用户</th><th>学习轮次</th><th>时间 / 反馈</th><th>类型 / 模式</th><th>阶段</th><th>实际间隔</th><th>下次复习</th><th>当日状态</th></tr></thead><tbody><tr v-for="row in result.items" :key="value(row,'id')"><td>{{value(row,'word_term')}}<br>{{value(row,'nickname')}} · {{value(row,'short_id')}}</td><td><div v-for="round in row.rounds" :key="value(round,'id')">{{value(round,'book_name')}} · 第 {{value(round,'round_no')}} 轮</div></td><td>{{time(value(row,'event_at'))}}<br>{{label(value(row,'feedback'))}}</td><td>{{label(value(row,'source_kind'))}}<br>{{label(value(row,'interval_mode'))}}</td><td>{{value(row,'before_stage')}} → {{value(row,'after_stage')}}</td><td>{{value(row,'before_interval')}} → {{value(row,'after_interval')}} 天</td><td>{{value(row,'next_due') || '待巩固完成'}}</td><td>{{value(row,'completed') ? '已完成' : '待巩固'}} · 连续记得 {{value(row,'streak')}}</td></tr><tr v-if="!result.items.length"><td colspan="8">暂无新规则反馈记录；历史学习记录仍保留在原列表。</td></tr></tbody></table></div>
  <div class="pager"><button :disabled="page===1" @click="page--; load()">上一页</button><span>第 {{page}} 页 · {{result.total}} 条</span><button :disabled="page*20>=result.total" @click="page++; load()">下一页</button></div>
 </div>
</template>
<style scoped>.study-events{padding:24px;margin-bottom:24px}.mode-row{margin-bottom:24px}.mode-row select{margin-left:24px;padding:8px}.mode-row p{color:#61746b}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:13px}th,td{text-align:left;padding:12px;border-bottom:1px solid #e5ebe6;white-space:nowrap}.pager{display:flex;gap:16px;justify-content:flex-end;margin-top:18px}</style>
