<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue';
import { request } from '../../services/request.js';
const items = ref([]),
  page = ref(1),
  busy = ref(false),
  error = ref(''),
  form = ref({ id: '', title: '', body: '', state: 'draft', publishAt: '' });
const labels = { draft: '草稿', published: '已发布', withdrawn: '已撤回' };
const format = (value) =>
  value
    ? new Intl.DateTimeFormat('zh-CN', {
        timeZone: 'Asia/Shanghai',
        dateStyle: 'short',
        timeStyle: 'short',
      }).format(new Date(value))
    : '未设置';
function inputTime(value) {
  if (!value) return '';
  const d = new Date(new Date(value).getTime() + 8 * 3600000);
  return d.toISOString().slice(0, 16);
}
async function load() {
  error.value = '';
  try {
    items.value = await request(`/admin/announcements?page=${page.value}&pageSize=50`);
  } catch (e) {
    error.value = e.message;
  }
}
async function edit(id) {
  try {
    const item = await request(`/admin/announcements/${id}`);
    form.value = { ...item, publishAt: inputTime(item.publishAt) };
  } catch (e) {
    error.value = e.message;
  }
}
function reset() {
  form.value = { id: '', title: '', body: '', state: 'draft', publishAt: '' };
}
async function save() {
  busy.value = true;
  error.value = '';
  try {
    const body = {
      title: form.value.title,
      body: form.value.body,
      state: form.value.state,
      publishAt: form.value.publishAt
        ? new Date(form.value.publishAt + ':00+08:00').toISOString()
        : null,
    };
    await request('/admin/announcements' + (form.value.id ? '/' + form.value.id : ''), {
      method: form.value.id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    });
    reset();
    await load();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  load();
  window.addEventListener('admin-token-updated', load);
});
onBeforeUnmount(() => window.removeEventListener('admin-token-updated', load));
</script>
<template>
  <section class="panel announcement-panel"
    ><h2>公告管理</h2
    ><p
      >公告按发布时间展示给小程序用户。未来时间的已发布公告将在到点后可见；草稿和撤回内容仅管理员可见。</p
    ><p v-if="error" role="alert">{{ error }}</p>
    <form @submit.prevent="save"
      ><label>标题<input v-model.trim="form.title" maxlength="120" required /></label
      ><label>正文<textarea v-model="form.body" maxlength="20000" rows="8" required /></label
      ><label
        >状态<select v-model="form.state"
          ><option value="draft">草稿</option
          ><option value="published">已发布</option
          ><option value="withdrawn">撤回</option></select
        ></label
      ><label
        >发布时间（北京时间，发布时留空为立即发布）<input
          v-model="form.publishAt"
          type="datetime-local" /></label
      ><button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '保存公告' }}</button
      ><button type="button" @click="reset">新建 / 清空</button></form
    >
    <table
      ><thead
        ><tr><th>标题</th><th>状态</th><th>发布时间（北京）</th><th>操作</th></tr></thead
      ><tbody
        ><tr v-for="item in items" :key="item.id"
          ><td>{{ item.title }}</td
          ><td>{{ labels[item.state] }}</td
          ><td>{{ format(item.publishAt) }}</td
          ><td><button @click="edit(item.id)">查看 / 编辑</button></td></tr
        ></tbody
      ></table
    ><p v-if="!items.length">暂无公告</p
    ><button
      :disabled="page === 1"
      @click="
        page--;
        load();
      "
      >上一页</button
    ><span> 第 {{ page }} 页 </span
    ><button
      :disabled="items.length < 50"
      @click="
        page++;
        load();
      "
      >下一页</button
    ></section
  >
</template>
<style scoped>
.announcement-panel {
  padding: 24px;
  background: white;
  border-radius: 16px;
}
form {
  display: grid;
  gap: 12px;
  max-width: 800px;
  margin: 24px 0;
}
label {
  display: grid;
  gap: 6px;
}
input,
textarea,
select {
  padding: 10px;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  font: inherit;
}
table {
  width: 100%;
  text-align: left;
  border-collapse: collapse;
}
td,
th {
  padding: 12px;
  border-bottom: 1px solid #eee;
}
button {
  cursor: pointer;
}
p[role='alert'] {
  color: #ad2525;
}
</style>
