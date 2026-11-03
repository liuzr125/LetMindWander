<script setup>
import { computed, onMounted, onBeforeUnmount, ref, reactive } from "vue";
import { request, getAdminToken } from "../../services/request.js";
const api = "/admin/english-books";
const settings = ref(null),
  showSettings = ref(false);
const rows = ref([]),
  keyword = ref(""),
  page = ref(1),
  hasMore = ref(false),
  options = ref({}),
  current = ref(null);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  candidates = ref([]),
  relatedCandidates = ref([]),
  searchTitle = ref("");
const provider = ref("gutenberg");
const sourceId = ref(""),
  selected = ref(0),
  checks = ref([]),
  chapterCount = ref(5),
  paste = ref(""),
  urls = reactive({});
const doc = computed(() => current.value?.document),
  chapter = computed(() => doc.value?.chapters[selected.value]);
const active = computed(() =>
  current.value?.jobs?.some((j) =>
    ["queued", "running", "pausing"].includes(j.state),
  ),
);
const labels = {
  draft: "草稿",
  published: "已发布",
  offline: "已下架",
  queued: "排队中",
  running: "处理中",
  pausing: "暂停中",
  paused: "已暂停",
  failed: "处理失败",
  completed: "已完成",
  cancelled: "已取消",
  audio: "生成音频",
  pictures: "生成配图",
  collect: "采集全文",
  original: "创作正文",
};
const id = () => crypto.randomUUID().replaceAll("-", "");
function fresh(title = "新英语书籍") {
  return {
    title,
    author: "",
    edition: "",
    difficulty: "intro",
    summary: "",
    kind: "licensed",
    sourceUrl: "",
    licenseNote: "",
    rightsScope: "",
    rightsConfirmed: false,
    audioRightsConfirmed: false,
    imagesRightsConfirmed: false,
    completenessConfirmed: false,
    voice: "luca",
    artStyle: "warm watercolor storybook",
    characterGuide: "",
    chapters: [],
  };
}
async function run(action) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await action();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
async function loadSettings() {
  const value = await request(`${api}/settings`, { silentError: true });
  settings.value = { ...value, imageApiKey: "", sourceApiKey: "" };
}
async function saveSettings() {
  await run(async () => {
    const value = await request(`${api}/settings`, {
      method: "PUT",
      body: JSON.stringify(
        Object.fromEntries(
          [
            "revision",
            "workerEnabled",
            "catalogUrl",
            "licensedCatalogUrl",
            "textUrlTemplate",
            "imageEndpoint",
            "imageModel",
            "maxJobCost",
            "imageUnitPrice",
            "ttsUnitPrice",
            "imageApiKey",
            "sourceApiKey",
          ].map((key) => [key, settings.value[key]]),
        ),
      ),
    });
    settings.value = { ...value, imageApiKey: "", sourceApiKey: "" };
    options.value = await request(`${api}/options`);
    notice.value = "配置已保存并即时生效，密钥不会回显。";
  });
}
async function list() {
  loading.value = true;
  try {
    const r = await request(
      `${api}?keyword=${encodeURIComponent(keyword.value)}&page=${page.value}`,
      { silentError: true },
    );
    rows.value = r.items;
    hasMore.value = r.hasMore;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function open(book) {
  await run(async () => {
    current.value = await request(`${api}/${book.id}`);
    selected.value = 0;
    checks.value = [];
    await previews();
  });
}
async function create(candidate) {
  await run(async () => {
    const d = fresh(candidate?.title || searchTitle.value || "新英语书籍");
    if (candidate) {
      d.author = candidate.author;
      d.sourceUrl = candidate.sourceUrl;
      sourceId.value = candidate.sourceId;
      provider.value = candidate.provider || "gutenberg";
      d.edition = candidate.edition || "";
      d.licenseNote = candidate.licenseNote || "";
      d.rightsScope = candidate.rightsScope || "";
    } else sourceId.value = "";
    current.value = await request(api, {
      method: "POST",
      body: JSON.stringify(d),
    });
    selected.value = 0;
    checks.value = [];
    await list();
  });
}
async function save() {
  current.value = await request(`${api}/${current.value.id}`, {
    method: "PUT",
    body: JSON.stringify({
      revision: current.value.revision,
      document: doc.value,
    }),
  });
  notice.value = "草稿已保存";
  await list();
}
async function search() {
  await run(async () => {
    const result = await request(
      `${api}/search?title=${encodeURIComponent(searchTitle.value)}`,
    );
    candidates.value = result.items || [];
    relatedCandidates.value = result.relatedItems || [];
    if (result.warnings?.length) notice.value = result.warnings.join("；");
    else if (!candidates.value.length)
      notice.value =
        "未找到目标书的匹配版本。相近书目是不同作品，可展开查看；也可以导入目标书的授权全文。";
  });
}
async function generate(kind, one = false, force = false) {
  await run(async () => {
    await save();
    current.value = await request(`${api}/${current.value.id}/jobs/${kind}`, {
      method: "POST",
      body: JSON.stringify({
        sourceId: Number(sourceId.value),
        provider: provider.value,
        force,
        chapterCount: Number(chapterCount.value),
        ...(one && chapter.value ? { chapterId: chapter.value.id } : {}),
      }),
    });
    notice.value = "任务已加入队列，完成后请预览检查。";
  });
}
async function control(job, action) {
  await run(async () => {
    current.value = await request(
      `${api}/${current.value.id}/jobs/${job.id}/${action}`,
      {
        method: "POST",
      },
    );
  });
}
async function upload(file, chapterId) {
  const data = new FormData();
  data.append("file", file);
  const base = import.meta.env.VITE_API_BASE_URL || "/api";
  const path = chapterId
    ? `/${current.value.id}/chapters/${chapterId}/pictures`
    : `/${current.value.id}/import`;
  const r = await fetch(`${base}${api}${path}`, {
    method: "POST",
    headers: { "X-Admin-Token": getAdminToken() },
    body: data,
  });
  const body = await r.json();
  if (!r.ok) throw new Error(body.message || "导入失败");
  current.value = body;
  selected.value = Math.min(
    selected.value,
    Math.max(0, doc.value.chapters.length - 1),
  );
  await previews();
  notice.value = "导入完成，请核对目录及内容完整性";
}
function importFile(event) {
  const f = event.target.files?.[0];
  if (f)
    run(async () => {
      await save();
      await upload(f);
    });
  event.target.value = "";
}
function importPicture(event) {
  const f = event.target.files?.[0];
  if (f)
    run(async () => {
      await save();
      await upload(f, chapter.value.id);
    });
  event.target.value = "";
}
function importPaste() {
  run(async () => {
    await save();
    await upload(new File([paste.value], "book.md", { type: "text/markdown" }));
    paste.value = "";
  });
}
function addChapter() {
  doc.value.chapters.push({
    id: id(),
    title: `Chapter ${doc.value.chapters.length + 1}`,
    paragraphs: [{ id: id(), text: "", translation: "" }],
    pictures: [],
    audio: [],
  });
  selected.value = doc.value.chapters.length - 1;
}
function moveChapter(delta) {
  const index = selected.value,
    to = index + delta;
  if (to < 0 || to >= doc.value.chapters.length) return;
  const [c] = doc.value.chapters.splice(index, 1);
  doc.value.chapters.splice(to, 0, c);
  selected.value = to;
}
async function previews() {
  const c = chapter.value;
  if (!c) return;
  for (const a of [...c.pictures, ...c.audio.slice(0, 1)]) {
    try {
      urls[a.assetId] = (
        await request(`${api}/${current.value.id}/media/${a.assetId}`, {
          silentError: true,
        })
      ).url;
    } catch {
      urls[a.assetId] = "";
    }
  }
}
function selectChapter(i) {
  selected.value = i;
  previews();
}
async function checkPublish() {
  await run(async () => {
    await save();
    checks.value = (await request(`${api}/${current.value.id}/checks`)).errors;
    if (!checks.value.length) notice.value = "发布检查通过，可以发布";
  });
}
async function publish() {
  await run(async () => {
    await save();
    current.value = await request(`${api}/${current.value.id}/publish`, {
      method: "POST",
      body: JSON.stringify({ revision: current.value.revision }),
    });
    notice.value = "已发布图文听读完整版";
    await list();
  });
}
async function resetGeneration(key) {
  if (
    !window.confirm(
      "请先到供应商核对：确认此请求未产生结果且无需恢复，才允许重新提交。再次生成可能产生费用。",
    )
  )
    return;
  await run(async () => {
    current.value = await request(
      `${api}/${current.value.id}/generation/${key}/reset`,
      {
        method: "POST",
        body: JSON.stringify({ confirmedNoResult: true }),
      },
    );
    notice.value = "已允许重新提交，请继续原任务。";
  });
}
async function recoverGeneration(event, key) {
  const file = event.target.files?.[0];
  if (!file) return;
  await run(async () => {
    const data = new FormData();
    data.append("file", file);
    const base = import.meta.env.VITE_API_BASE_URL || "/api";
    const response = await fetch(
      `${base}${api}/${current.value.id}/generation/${key}/recover`,
      {
        method: "POST",
        headers: { "X-Admin-Token": getAdminToken() },
        body: data,
      },
    );
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || "恢复失败");
    current.value = body;
    notice.value = "供应商结果已保存，可以继续任务。";
  });
}
async function regenerate(kind) {
  if (
    !window.confirm(
      "将生成本章的新版本，可能产生费用；线上内容在重新发布前继续使用旧版本。",
    )
  )
    return;
  await generate(kind, true, true);
}
async function unpublish() {
  await run(async () => {
    current.value = await request(`${api}/${current.value.id}/unpublish`, {
      method: "POST",
    });
    await list();
  });
}
let timer;
onMounted(async () => {
  await list();
  try {
    options.value = await request(`${api}/options`);
    await loadSettings();
  } catch (e) {
    error.value = e.message;
  }
  timer = setInterval(async () => {
    if (!current.value || busy.value) return;
    try {
      const r = await request(`${api}/${current.value.id}/tasks`, {
        silentError: true,
      });
      if (
        active.value &&
        !r.jobs.some((j) => ["queued", "running", "pausing"].includes(j.state))
      ) {
        current.value = await request(`${api}/${current.value.id}`);
        await previews();
        await list();
      } else {
        current.value.jobs = r.jobs;
        current.value.unresolved = r.unresolved;
      }
    } catch {}
  }, 4000);
});
onBeforeUnmount(() => clearInterval(timer));
</script>

<template>
  <div class="books-page">
    <header>
      <div>
        <div class="eyebrow">ENGLISH LIBRARY</div>
        <h1>英语书籍</h1>
        <p>把一本完整的英语书，变成可以看、听、学习的故事。</p>
      </div>
      <button @click="create()" :disabled="busy">＋ 新建书籍</button>
    </header>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div>
    <div v-if="notice" class="alert success" role="status">{{ notice }}</div>
    <div v-if="!options.workerEnabled" class="alert">
      生成任务尚未启用。完成数据库迁移、书源与语音／图像配置后，可启用后台处理；仍可编辑及导入草稿。
    </div>
    <section class="panel setup" v-if="settings">
      <div class="row">
        <div>
          <h2>接入配置与就绪检查</h2>
          <p>
            配置保存后即时生效。留空密钥会保留已有值；TTS 按每个请求每 100
            个有效字符向上取整计费。
          </p>
        </div>
        <button class="secondary" @click="showSettings = !showSettings">
          {{ showSettings ? "收起配置" : "配置服务" }}
        </button>
      </div>
      <div class="readiness">
        <span :class="{ ready: settings.databaseReady }"
          >数据库 {{ settings.databaseReady ? "已就绪" : "待迁移" }}</span
        ><span :class="{ ready: settings.ttsReady }"
          >TTS {{ settings.ttsReady ? "已配置" : "待配置" }}</span
        ><span :class="{ ready: settings.ossReady }"
          >OSS {{ settings.ossReady ? "已配置" : "待配置" }}</span
        ><span :class="{ ready: settings.imageReady }"
          >图像 {{ settings.imageReady ? "已配置" : "待配置" }}</span
        ><span :class="{ ready: settings.workerEnabled }"
          >生成任务 {{ settings.workerEnabled ? "已启用" : "已关闭" }}</span
        >
      </div>
      <form
        class="settings-form"
        v-if="showSettings"
        @submit.prevent="saveSettings"
      >
        <fieldset :disabled="busy">
          <div class="checkboxes">
            <label
              ><input
                type="checkbox"
                v-model="settings.workerEnabled"
              />启用书籍生成任务</label
            >
          </div>
          <div class="form-grid">
            <label
              >单任务费用上限（元）<input
                v-model="settings.maxJobCost"
                inputmode="decimal" /></label
            ><label
              >图像每张价格（元）<input
                v-model="settings.imageUnitPrice"
                placeholder="按现有套餐或服务商报价填写"
                inputmode="decimal" /></label
            ><label
              >TTS 每千次计费调用价格（元）<input
                v-model="settings.ttsUnitPrice"
                placeholder="按现有套餐或服务商报价填写"
                inputmode="decimal"
            /></label>
          </div>
          <div class="form-grid">
            <label
              >兼容图像接口 HTTPS 地址<input
                v-model="settings.imageEndpoint"
                placeholder="完整图像生成接口地址" /></label
            ><label
              >图像模型名称<input
                v-model="settings.imageModel"
                placeholder="服务商提供的模型标识" /></label
            ><label
              >图像 API Key<input
                type="password"
                autocomplete="new-password"
                v-model="settings.imageApiKey"
                :placeholder="
                  settings.imageKeyConfigured
                    ? '已配置，留空保持'
                    : '填写密钥，仅加密保存'
                "
            /></label>
          </div>
          <div class="form-grid">
            <label
              >合作正版书目 API 根地址<input
                v-model="settings.licensedCatalogUrl"
                placeholder="已有合作接口时填写" /></label
            ><label
              >合作书源 API Key（可选）<input
                type="password"
                autocomplete="new-password"
                v-model="settings.sourceApiKey"
                :placeholder="
                  settings.sourceKeyConfigured
                    ? '已配置，留空保持'
                    : '按书源要求填写'
                " /></label
            ><label
              >公版书目 API 根地址<input
                v-model="settings.catalogUrl"
                placeholder="https://gutendex.com"
            /></label>
          </div>
          <label
            >授权全文下载 URL 模板<input
              v-model="settings.textUrlTemplate"
              placeholder="https://允许自动下载的书源路径/{id}.txt"
          /></label>
          <p class="hint">
            就绪状态只表示本地配置可用，真实接口连通性和 OSS
            权限仍需联调。配置不完整时可先保存，相关生成按钮会给出缺失项。
          </p>
          <button :disabled="busy" type="submit">保存接入配置</button>
        </fieldset>
      </form>
    </section>
    <section class="panel finder">
      <h2>输入书名，寻找合适版本</h2>
      <p>
        优先发现可收录书源；每个具体版本的正文、插图与音频使用范围分别确认。
      </p>
      <form @submit.prevent="search">
        <input
          v-model="searchTitle"
          placeholder="例如：小王子 / Alice's Adventures in Wonderland"
          maxlength="200"
        /><button :disabled="busy || !searchTitle.trim()">联网找书</button>
      </form>
      <div
        class="candidate"
        v-for="c in candidates"
        :key="c.provider + ':' + c.sourceId"
      >
        <div>
          <strong>{{ c.title }}</strong
          ><small
            >{{ c.author }} ·
            {{ c.provider === "licensed" ? "合作正版书源" : "Gutenberg" }} #{{
              c.sourceId
            }}
            · 待核实使用范围</small
          ><a :href="c.sourceUrl" target="_blank" rel="noopener noreferrer"
            >查看书源 ↗</a
          >
        </div>
        <button class="secondary" :disabled="busy" @click="create(c)">
          建立草稿
        </button>
      </div>
    </section>
    <section class="panel">
      <div class="row">
        <h2>书籍列表</h2>
        <form
          @submit.prevent="
            page = 1;
            list();
          "
        >
          <input v-model="keyword" placeholder="搜索已添加的书籍" /><button
            class="secondary"
          >
            搜索
          </button>
        </form>
      </div>
      <p v-if="loading">正在读取书库…</p>
      <p v-else-if="!rows.length" class="empty">
        书库还没有内容。从一个书名或一份授权文件开始。
      </p>
      <div class="book-grid">
        <button
          class="book-card"
          v-for="b in rows"
          :key="b.id"
          @click="open(b)"
        >
          <div class="cover">
            <span>EN</span><b>{{ b.title }}</b>
          </div>
          <div class="book-meta">
            <span class="tag">{{ labels[b.state] }}</span>
            <h3>{{ b.title }}</h3>
            <p>{{ b.author || "作者待补充" }}</p>
            <small
              >{{ b.chapterCount }} 章 · {{ b.pictureCount }} 幅图 ·
              {{ b.audioCount }} 段音频</small
            >
          </div>
        </button>
      </div>
      <div class="pager">
        <button
          class="secondary"
          :disabled="page <= 1"
          @click="
            page--;
            list();
          "
        >
          上一页</button
        ><span>第 {{ page }} 页</span
        ><button
          class="secondary"
          :disabled="!hasMore"
          @click="
            page++;
            list();
          "
        >
          下一页
        </button>
      </div>
    </section>
    <section v-if="doc" class="panel editor">
      <div class="row">
        <div>
          <h2>{{ doc.title }}</h2>
          <p>
            草稿版本 {{ current.revision }} · {{ labels[current.state]
            }}<span v-if="current.publishedRevision">
              · 线上版本 {{ current.publishedRevision }}</span
            >
          </p>
        </div>
        <div class="actions">
          <button
            class="secondary"
            :disabled="busy || active"
            @click="run(save)"
          >
            保存草稿</button
          ><button :disabled="busy || active" @click="publish">
            发布完整版</button
          ><button
            v-if="current.state === 'published'"
            class="secondary"
            :disabled="busy"
            @click="unpublish"
          >
            下架
          </button>
        </div>
      </div>
      <fieldset :disabled="busy || active">
        <div class="form-grid">
          <label>书名<input v-model="doc.title" maxlength="240" /></label
          ><label>作者<input v-model="doc.author" /></label
          ><label>版本／译者<input v-model="doc.edition" /></label
          ><label
            >内容类型<select v-model="doc.kind">
              <option value="licensed">授权书籍</option>
              <option value="public_domain">公版书籍</option>
              <option value="ai_original">AI 原创</option>
              <option value="adapted">学习改写版</option>
              <option value="excerpt">节选</option>
            </select></label
          ><label
            >难度<select v-model="doc.difficulty">
              <option value="intro">入门</option>
              <option value="intermediate">中级</option>
              <option value="advanced">进阶</option>
            </select></label
          ><label
            >英文音色<select v-model="doc.voice">
              <option
                v-for="v in options.voices || []"
                :key="v.code"
                :value="v.code"
              >
                {{ v.label || v.name || v.code }}
              </option>
            </select></label
          >
        </div>
        <label
          >简介／原创主题<textarea
            v-model="doc.summary"
            rows="3"
            placeholder="介绍书籍；AI 原创时填写故事主题与创作要求"
          />
        </label>
        <div class="form-grid">
          <label
            >来源链接<input
              v-model="doc.sourceUrl"
              placeholder="https://…" /></label
          ><label
            >适用范围<input
              v-model="doc.rightsScope"
              placeholder="投放地区、使用期限及用途"
          /></label>
        </div>
        <label
          >来源与许可依据<textarea
            v-model="doc.licenseNote"
            rows="2"
            placeholder="具体版本及正文、图像、语音合成和分发的使用依据"
          />
        </label>
        <div class="checkboxes">
          <label
            ><input
              type="checkbox"
              v-model="doc.rightsConfirmed"
            />正文可收录</label
          ><label
            ><input
              type="checkbox"
              v-model="doc.imagesRightsConfirmed"
            />配图可使用</label
          ><label
            ><input
              type="checkbox"
              v-model="doc.audioRightsConfirmed"
            />允许语音合成及分发</label
          ><label
            ><input
              type="checkbox"
              v-model="doc.completenessConfirmed"
            />已核对全书目录与完整性</label
          >
        </div>
        <p class="hint">
          修改正文后需先保存，再重新确认完整性。节选不能发布为完整版。
        </p>
        <div class="form-grid">
          <label>全书画风<input v-model="doc.artStyle" /></label
          ><label
            >人物外貌与一致性参考<input v-model="doc.characterGuide"
          /></label>
        </div>
        <div class="toolbar">
          <label class="file-button"
            >导入 TXT / Markdown / EPUB<input
              type="file"
              accept=".txt,.md,.epub"
              @change="importFile" /></label
          ><input
            v-model="sourceId"
            class="source-id"
            placeholder="Gutenberg 编号"
          /><button
            class="secondary"
            @click="generate('collect')"
            :disabled="!sourceId || doc.chapters.length > 0"
          >
            采集全文</button
          ><template v-if="doc.kind === 'ai_original'"
            ><input
              class="source-id"
              type="number"
              min="1"
              max="20"
              v-model="chapterCount"
            /><button class="secondary" @click="generate('original')">
              生成原创正文
            </button></template
          >
        </div>
        <details>
          <summary>粘贴整本正文</summary>
          <p class="hint">
            使用 # Chapter 1
            等标题分章，章节间保留空行。导入会替换当前草稿正文。
          </p>
          <textarea
            v-model="paste"
            rows="7"
            placeholder="# Chapter 1&#10;&#10;English paragraphs…"
          /><button
            class="secondary"
            :disabled="!paste.trim()"
            @click="importPaste"
          >
            导入粘贴内容
          </button>
        </details>
        <div class="toolbar">
          <button
            class="secondary"
            :disabled="!doc.chapters.length"
            @click="generate('pictures')"
          >
            补齐全书配图</button
          ><button
            class="secondary"
            :disabled="!doc.chapters.length"
            @click="generate('audio')"
          >
            生成／补齐全书音频</button
          ><button class="secondary" @click="checkPublish">检查发布条件</button>
        </div>
        <div class="chapter-layout" v-if="doc.chapters.length">
          <aside>
            <h3>全书目录 · {{ doc.chapters.length }} 章</h3>
            <button
              v-for="(c, i) in doc.chapters"
              :key="c.id"
              :class="{ chosen: i === selected }"
              @click="selectChapter(i)"
            >
              <b>{{ i + 1 }}. {{ c.title }}</b
              ><small
                >{{ c.pictures.length }} 图 · {{ c.audio.length }} 段音频</small
              ></button
            ><button class="secondary" @click="addChapter">＋ 添加章节</button>
          </aside>
          <article v-if="chapter">
            <div class="row">
              <h3>章节编辑与预览</h3>
              <div class="actions">
                <button class="secondary" @click="moveChapter(-1)">上移</button
                ><button class="secondary" @click="moveChapter(1)">下移</button>
              </div>
            </div>
            <label>章节标题<input v-model="chapter.title" /></label>
            <div
              v-for="(p, i) in chapter.paragraphs"
              :key="p.id"
              class="paragraph"
            >
              <label
                >英文段落 {{ i + 1
                }}<textarea v-model="p.text" rows="4" /></label
              ><label
                >中文译文（可选）<textarea
                  v-model="p.translation"
                  rows="2"
                /></label
              ><button
                class="text-button"
                @click="chapter.paragraphs.splice(i, 1)"
              >
                删除段落
              </button>
            </div>
            <button
              class="secondary"
              @click="
                chapter.paragraphs.push({ id: id(), text: '', translation: '' })
              "
            >
              ＋ 添加段落
            </button>
            <div class="pictures">
              <figure v-for="(p, i) in chapter.pictures" :key="p.assetId">
                <img
                  v-if="urls[p.assetId]"
                  :src="urls[p.assetId]"
                  :alt="p.caption"
                />
                <figcaption>
                  {{ p.source === "ai" ? "AI 配图" : "原版／授权插图" }} ·
                  {{ p.caption }}
                </figcaption>
                <button
                  class="text-button"
                  @click="chapter.pictures.splice(i, 1)"
                >
                  移除此图
                </button>
              </figure>
            </div>
            <div class="toolbar">
              <label class="file-button"
                >上传授权图片<input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  @change="importPicture" /></label
              ><button class="secondary" @click="generate('pictures', true)">
                补齐本章配图</button
              ><button class="secondary" @click="generate('audio', true)">
                生成本章音频</button
              ><button class="text-button" @click="regenerate('pictures')">
                重新绘制本章</button
              ><button class="text-button" @click="regenerate('audio')">
                重生成本章音频
              </button>
            </div>
            <div v-if="chapter.audio.length">
              <p>
                {{
                  chapter.audio.length
                }}
                个音频片段，按正文顺序播放。首段试听：
              </p>
              <audio
                v-if="urls[chapter.audio[0].assetId]"
                controls
                :src="urls[chapter.audio[0].assetId]"
              />
            </div>
          </article>
        </div>
        <button v-else class="secondary" @click="addChapter">
          ＋ 手工添加章节
        </button>
      </fieldset>
      <div v-if="checks.length" class="alert">
        <strong>待补齐项目</strong>
        <ul>
          <li v-for="(item, i) in checks" :key="i">{{ item }}</li>
        </ul>
      </div>
      <div v-if="current.unresolved?.length" class="alert">
        <strong>生成请求结果待核对</strong>
        <p>
          先在供应商侧核对结果。已有结果可以导入后继续；确认未产生结果后才能允许重新提交。
        </p>
        <div
          v-for="item in current.unresolved"
          :key="item.step_key"
          class="job"
        >
          <small>请求 {{ item.step_key.slice(0, 12) }}…</small>
          <div class="actions">
            <label class="file-button"
              >导入供应商结果<input
                type="file"
                @change="recoverGeneration($event, item.step_key)"
                :disabled="busy || active" /></label
            ><button
              class="secondary"
              @click="resetGeneration(item.step_key)"
              :disabled="busy || active"
            >
              确认未生成，允许重试
            </button>
          </div>
        </div>
      </div>
      <div class="jobs" v-if="current.jobs.length">
        <h3>处理任务</h3>
        <div v-for="j in current.jobs" :key="j.id" class="job">
          <div>
            <strong
              >{{ labels[j.kind] || j.kind }} ·
              {{ labels[j.state] || j.state }}</strong
            ><small
              >{{ j.completed_count }} / {{ j.total_count || "待计算" }} ·
              已生成用量
              {{ j.spent_units || 0 }}
              {{
                j.kind === "pictures"
                  ? "张"
                  : j.kind === "audio"
                    ? "计费调用"
                    : ""
              }}
              · {{ j.message }}</small
            >
          </div>
          <div class="actions">
            <button
              class="secondary"
              v-if="['queued', 'running'].includes(j.state)"
              @click="control(j, 'pause')"
              :disabled="busy"
            >
              暂停</button
            ><button
              class="secondary"
              v-if="['paused', 'failed'].includes(j.state)"
              @click="control(j, 'resume')"
              :disabled="busy || active"
            >
              继续／重试</button
            ><button
              class="text-button"
              v-if="['queued', 'running', 'paused', 'failed'].includes(j.state)"
              @click="control(j, 'cancel')"
              :disabled="busy"
            >
              取消
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.readiness {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}
.readiness span {
  padding: 7px 12px;
  border-radius: 20px;
  background: #fff4df;
  color: #9b7835;
  font-size: 12px;
}
.readiness .ready {
  background: #eaf4ee;
  color: #315c45;
}
.settings-form {
  display: block;
  max-width: none;
}
.settings-form fieldset {
  width: 100%;
}
.books-page {
  max-width: 1320px;
  margin: auto;
  color: #243b53;
}
header,
.row,
.actions,
.toolbar,
form,
.candidate,
.job {
  display: flex;
  align-items: center;
  gap: 12px;
}
header,
.row,
.candidate,
.job {
  justify-content: space-between;
}
header {
  margin-bottom: 26px;
}
.eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #648b79;
}
h1 {
  font-size: 30px;
  margin: 7px 0;
}
h2 {
  font-size: 20px;
  margin: 0 0 10px;
}
h3 {
  font-size: 16px;
}
p {
  color: #788795;
  font-size: 14px;
  line-height: 1.7;
}
button,
.file-button {
  border: 0;
  border-radius: 9px;
  padding: 10px 16px;
  background: #315c45;
  color: white;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
button:disabled,
fieldset:disabled {
  opacity: 0.6;
  cursor: default;
}
.secondary,
.file-button {
  background: #eef4f1;
  color: #315c45;
}
.text-button {
  background: none;
  color: #627587;
  padding: 6px;
  font-size: 12px;
}
.panel {
  background: #fff;
  border: 1px solid #e4ebe7;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 22px;
}
input,
textarea,
select {
  box-sizing: border-box;
  border: 1px solid #d9e3dd;
  border-radius: 8px;
  background: white;
  color: #243b53;
  padding: 10px 12px;
  font: inherit;
  width: 100%;
  min-width: 0;
}
textarea {
  resize: vertical;
}
form {
  flex: 1;
  max-width: 720px;
}
form input {
  flex: 1;
}
form button {
  white-space: nowrap;
}
label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 13px;
  color: #627587;
  margin: 12px 0;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}
.checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}
.checkboxes label {
  flex-direction: row;
  align-items: center;
}
.checkboxes input {
  width: auto;
}
.hint {
  font-size: 12px;
}
.alert {
  padding: 14px 18px;
  border-radius: 10px;
  background: #fff7e7;
  color: #8b672a;
  margin-bottom: 18px;
  line-height: 1.6;
  font-size: 14px;
}
.error {
  background: #fff0ee;
  color: #a94139;
}
.success {
  background: #eaf5ee;
  color: #315c45;
}
.candidate {
  padding: 18px 0;
  border-top: 1px solid #eef2ef;
}
.candidate small,
.job small,
.book-card small {
  display: block;
  color: #86948d;
  margin-top: 6px;
}
a {
  font-size: 12px;
  color: #315c45;
}
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
  gap: 16px;
}
.book-card {
  display: flex;
  text-align: left;
  background: #fafcfb;
  border: 1px solid #e6ece8;
  padding: 16px;
  gap: 16px;
  color: #243b53;
}
.cover {
  background: linear-gradient(150deg, #315c45, #88a793);
  color: white;
  width: 85px;
  min-width: 85px;
  height: 120px;
  box-sizing: border-box;
  border-radius: 4px 10px 10px 4px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.cover span {
  font-size: 10px;
  letter-spacing: 2px;
}
.cover b {
  font-size: 13px;
  overflow: hidden;
}
.book-meta h3 {
  margin: 7px 0;
}
.book-meta p {
  margin: 5px 0;
}
.tag {
  font-size: 11px;
  color: #557862;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 15px;
  margin-top: 18px;
  font-size: 13px;
}
.empty {
  padding: 30px;
  text-align: center;
}
fieldset {
  padding: 0;
  border: 0;
  min-width: 0;
}
.toolbar {
  flex-wrap: wrap;
  margin: 18px 0;
}
.source-id {
  max-width: 150px;
}
.file-button {
  display: inline-flex;
  margin: 0;
  cursor: pointer;
}
.file-button input {
  display: none;
}
.chapter-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 24px;
  border-top: 1px solid #e7ede9;
  padding-top: 18px;
}
aside button {
  width: 100%;
  margin-bottom: 8px;
  text-align: left;
  background: #f5f8f6;
  color: #567060;
}
aside button.chosen {
  background: #e1eee6;
  color: #22452f;
}
aside small {
  display: block;
  font-size: 11px;
  margin-top: 6px;
}
.paragraph {
  background: #fafcfb;
  border: 1px solid #edf1ee;
  border-radius: 10px;
  padding: 12px;
  margin: 14px 0;
}
.pictures {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
  margin-top: 18px;
}
figure {
  margin: 0;
}
figure img {
  width: 100%;
  border-radius: 12px;
}
figcaption {
  font-size: 12px;
  color: #839087;
}
details {
  margin: 14px 0;
}
summary {
  cursor: pointer;
  color: #557862;
  font-size: 14px;
}
.jobs {
  margin-top: 24px;
  border-top: 1px solid #e7ede9;
  padding-top: 12px;
}
.job {
  padding: 14px 0;
  border-bottom: 1px solid #eef2ef;
  font-size: 13px;
}
audio {
  width: 100%;
}
@media (max-width: 950px) {
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .chapter-layout {
    grid-template-columns: 180px minmax(0, 1fr);
  }
  header,
  .row {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}
@media (max-width: 700px) {
  .form-grid,
  .chapter-layout {
    grid-template-columns: 1fr;
  }
  .panel {
    padding: 16px;
  }
}
</style>
