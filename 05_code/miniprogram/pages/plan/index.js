const { planService } = require('../../services/index');

const WEEKDAYS = [{ value: 0, label: '一' }, { value: 1, label: '二' }, { value: 2, label: '三' }, { value: 3, label: '四' }, { value: 4, label: '五' }, { value: 5, label: '六' }, { value: 6, label: '日' }];

Page({
  data: {
    loaded: false, saving: false, addingTopic: false, errorMessage: '', successMessage: '', newTopicName: '',
    budgets: [5, 10, 15, 20, 30], weekdays: WEEKDAYS,
    topics: [], selectedTopicIds: {}, isWeekdaySelected: {}, previewDateText: '', previewText: '', mode: 'plan',
    form: { versionNo: null, dailyBudgetMin: 10, weekdaysMask: 31, difficulty: 'intro', techCount: 1, newWordCount: 3, journalEnabled: true, reviewEnabled: true, reviewLimit: 5, paused: false, pauseUntil: null }
  },
  onLoad(options) { this.setData({ mode: options && options.mode === 'today' ? 'today' : 'plan' }); this.load(); },
  load() {
    Promise.all([planService.get(), planService.topics()]).then(([plan, topics]) => {
      const selected = {}; (plan.topics || []).forEach((topic) => { selected[topic.id] = true; });
      const form = { versionNo: plan.versionNo, dailyBudgetMin: plan.dailyBudgetMin, weekdaysMask: plan.weekdaysMask, difficulty: plan.difficulty, techCount: plan.techCount, newWordCount: plan.newWordCount, journalEnabled: plan.journalEnabled, reviewEnabled: plan.reviewEnabled, reviewLimit: plan.reviewLimit, paused: plan.paused, pauseUntil: plan.pauseUntil };
      this.setData({ form, topics, selectedTopicIds: selected, loaded: true, errorMessage: '' }); this.refreshPreview();
    }).catch((error) => { this.setData({ loaded: true, errorMessage: error.message || '计划加载失败，请重试' }); });
  },
  selectBudget(e) { this.setData({ 'form.dailyBudgetMin': e.currentTarget.dataset.value }); },
  toggleWeekday(e) { const bit = 1 << e.currentTarget.dataset.value; this.setData({ 'form.weekdaysMask': this.data.form.weekdaysMask ^ bit }); this.refreshPreview(); },
  toggleTopic(e) { const selected = Object.assign({}, this.data.selectedTopicIds); const id = e.currentTarget.dataset.id; selected[id] = !selected[id]; this.setData({ selectedTopicIds: selected }); },
  inputTopic(e) { this.setData({ newTopicName: e.detail.value }); },
  addTopic() {
    const name = (this.data.newTopicName || '').trim(); if (!name) return wx.showToast({ title: '请输入主题名称', icon: 'none' });
    this.setData({ addingTopic: true }); planService.createTopic(name).then((topic) => { const ids = Object.assign({}, this.data.selectedTopicIds); ids[topic.id] = true; const exists = this.data.topics.some((item) => item.id === topic.id); this.setData({ topics: exists ? this.data.topics : this.data.topics.concat(topic), selectedTopicIds: ids, newTopicName: '' }); }).catch((error) => wx.showToast({ title: error.message || '添加失败', icon: 'none' })).finally(() => this.setData({ addingTopic: false }));
  },
  selectDifficulty(e) { this.setData({ 'form.difficulty': e.currentTarget.dataset.value }); },
  changeCount(e) {
    const key = e.currentTarget.dataset.key;
    // dataset 值是字符串；先转数值，避免 1 + '-1' 变成 '1-1'，继而渲染为 null。
    const current = Number(this.data.form[key]);
    const delta = Number(e.currentTarget.dataset.delta);
    const safeCurrent = Number.isFinite(current) ? current : 0;
    const safeDelta = Number.isFinite(delta) ? delta : 0;
    const next = Math.max(0, safeCurrent + safeDelta);
    this.setData({ [`form.${key}`]: next });
  },
  inputCount(e) {
    const key = e.currentTarget.dataset.key;
    const value = Number(e.detail.value);
    this.setData({ [`form.${key}`]: Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0 });
  },
  toggleJournal(e) { this.setData({ 'form.journalEnabled': e.detail.value }); },
  toggleReview(e) { this.setData({ 'form.reviewEnabled': e.detail.value }); },
  togglePause() { this.setData({ 'form.paused': !this.data.form.paused }); this.refreshPreview(); },
  refreshPreview() { const d = new Date(); if (this.data.mode !== 'today') d.setDate(d.getDate() + 1); const mask = this.data.form.weekdaysMask; const active = !this.data.form.paused && (mask & (1 << ((d.getDay() + 6) % 7))); this.setData({ isWeekdaySelected: this.weekdayMap(mask), previewDateText: `${d.getMonth() + 1}月${d.getDate()}日`, previewText: active ? '活动日' : '非活动日(不自动安排任务)' }); },
  weekdayMap(mask) { const result = {}; WEEKDAYS.forEach((day) => { result[day.value] = !!(mask & (1 << day.value)); }); return result; },
  adjustToday() { this.submit(true); },
  savePlan() { this.submit(false); },
  submit(adjustToday) {
    const form = this.data.form; const topicIds = Object.keys(this.data.selectedTopicIds).filter((id) => this.data.selectedTopicIds[id]); const payload = Object.assign({}, form, { topicIds, adjustToday, changeReason: adjustToday ? '用户明确调整今日' : '计划设置保存' });
    this.setData({ saving: true, errorMessage: '', successMessage: '' }); planService.update(payload).then((plan) => {
      this.setData({ form: Object.assign({}, form, { versionNo: plan.versionNo, paused: plan.paused, pauseUntil: plan.pauseUntil }), successMessage: adjustToday ? '今日未开始任务已更新' : '计划已保存，明日生效' });
      this.refreshPreview();
      setTimeout(() => { this.setData({ successMessage: '' }); if (adjustToday && getCurrentPages().length > 1) wx.navigateBack(); }, 1200);
    }).catch((error) => { if (error.code === 'PLAN_VERSION_CONFLICT') { this.load(); wx.showToast({ title: '计划已更新，已为你刷新最新内容', icon: 'none' }); } else { this.setData({ errorMessage: error.message || '保存失败，请重试' }); } }).finally(() => this.setData({ saving: false }));
  }
});
