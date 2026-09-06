<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../../services/request.js'

const loading = ref(true)
const error = ref('')
const coverage = ref({ publishedTotal: 0, sourceCount: 0, technicalTopics: [], wordStages: [] })
const insufficient = computed(() => [...coverage.value.technicalTopics, ...coverage.value.wordStages].filter(item => item.itemCount < 25))

async function load() {
  loading.value = true; error.value = ''
  try { coverage.value = await request('/admin/content/coverage') }
  catch (e) { error.value = e.message || '内容统计加载失败' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>内容与来源</h1><p>核对技术主题与英语学段的初始化覆盖率，发布内容仍需保留来源与许可。</p></div><button class="refresh" @click="load">刷新统计</button></div>
    <div v-if="loading" class="panel state">正在统计内容…</div>
    <div v-else-if="error" class="panel state error">{{ error }}</div>
    <template v-else>
      <div class="metrics"><div class="panel metric"><span>已发布内容</span><strong>{{ coverage.publishedTotal }}</strong></div><div class="panel metric"><span>启用来源</span><strong>{{ coverage.sourceCount }}</strong></div><div class="panel metric"><span>未达 25 条</span><strong :class="{ danger: insufficient.length }">{{ insufficient.length }}</strong></div></div>
      <div v-if="insufficient.length" class="warning">以下分类不足 25 条：{{ insufficient.map(item => `${item.name}（${item.itemCount}）`).join('、') }}</div>
      <div class="grids">
        <div class="panel table-panel"><h2>技术知识主题</h2><table><thead><tr><th>主题</th><th>已发布</th><th>状态</th></tr></thead><tbody><tr v-for="item in coverage.technicalTopics" :key="item.name"><td>{{ item.name }}</td><td>{{ item.itemCount }}</td><td><span :class="item.itemCount >= 25 ? 'ok' : 'bad'">{{ item.itemCount >= 25 ? '达标' : '不足' }}</span></td></tr></tbody></table></div>
        <div class="panel table-panel"><h2>英语词汇学段</h2><table><thead><tr><th>学段</th><th>已发布</th><th>状态</th></tr></thead><tbody><tr v-for="item in coverage.wordStages" :key="item.name"><td>{{ item.name }}</td><td>{{ item.itemCount }}</td><td><span :class="item.itemCount >= 25 ? 'ok' : 'bad'">{{ item.itemCount >= 25 ? '达标' : '不足' }}</span></td></tr></tbody></table></div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.refresh{height:38px;padding:0 18px;border:1px solid #cfdcf0;border-radius:9px;color:#126ff4;background:#fff}.state{margin-top:24px;padding:54px;text-align:center;color:#77849a}.error{color:#c53f4c}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:24px}.metric{padding:22px}.metric span{display:block;color:#71809a;font-size:13px}.metric strong{display:block;margin-top:10px;font-size:30px}.danger{color:#d8424f}.warning{margin-top:18px;padding:13px 16px;border:1px solid #f2d59e;border-radius:10px;color:#955e12;background:#fff9ed}.grids{display:grid;grid-template-columns:1.4fr 1fr;gap:20px;margin-top:20px}.table-panel{padding:22px}.table-panel h2{margin:0 0 16px;font-size:18px}table{width:100%;border-collapse:collapse}th,td{padding:13px 12px;border-bottom:1px solid #edf0f5;text-align:left}th{color:#75819a;font-size:12px;font-weight:650}.ok,.bad{display:inline-block;padding:4px 10px;border-radius:99px;font-size:12px}.ok{color:#15845f;background:#e8f8f1}.bad{color:#c43a47;background:#fff0f1}
</style>
