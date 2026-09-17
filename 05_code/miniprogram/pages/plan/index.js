const { planService } = require('../../services/index');

Page({
  data: {
    loaded: false, saving: false, addingTopic: false, errorMessage: '', successMessage: '', newTopicName: '',
    settings: {}, budgets: [], weekdays: [], difficulties: [], dailyBudgetInput: '',
    topicPanelExpanded: false, selectedTopicCount: 0, selectedTopicSummary: '尚未选择，点击编辑',
    topics: [], selectedTopicIds: {}, isWeekdaySelected: {}, previewDateText: '', previewText: '', mode: 'plan',
    form: { versionNo: null, dailyBudgetMin: null, weekdaysMask: 0, difficulty: '', techCount: 0, newWordCount: 0, journalEnabled: false, reviewEnabled: false, reviewLimit: 0, paused: false, pauseUntil: null }
  },
  onLoad(options) { this.setData({ mode: options && options.mode === 'today' ? 'today' : 'plan' }); this.load(); },
  load() {
    Promise.all([planService.get(), planService.settings(), planService.topics()]).then(([plan, settings, topics]) => {
      const selected = {}; (plan.topics || []).forEach((topic) => { selected[topic.id] = true; });
      const form = { versionNo: plan.versionNo, dailyBudgetMin: plan.dailyBudgetMin, weekdaysMask: plan.weekdaysMask, difficulty: plan.difficulty, techCount: plan.techCount, newWordCount: plan.newWordCount, journalEnabled: plan.journalEnabled, reviewEnabled: plan.reviewEnabled, reviewLimit: plan.reviewLimit, paused: plan.paused, pauseUntil: plan.pauseUntil };
      const topicSummary = this.topicSummary(topics, selected);
      this.setData({ form, settings, budgets: settings.dailyBudgetPresets || [], weekdays: settings.weekdays || [], difficulties: settings.difficulties || [], dailyBudgetInput: String(plan.dailyBudgetMin), topics, selectedTopicIds: selected, selectedTopicCount: topicSummary.count, selectedTopicSummary: topicSummary.text, loaded: true, errorMessage: '' }); this.refreshPreview();
    }).catch((error) => { this.setData({ loaded: true, errorMessage: error.message || '计划加载失败，请重试' }); });
  },
  selectBudget(e) { const value = Number(e.currentTarget.dataset.value); this.setData({ 'form.dailyBudgetMin': value, dailyBudgetInput: String(value) }); },
  inputBudget(e) { const raw = e.detail.value; const value = Number(raw); const update = { dailyBudgetInput: raw }; if (raw !== '' && Number.isFinite(value)) update['form.dailyBudgetMin'] = Math.floor(value); this.setData(update); },
  commitBudget() { const value = this.normalizedBudget(); if (value === null) return; this.setData({ 'form.dailyBudgetMin': value, dailyBudgetInput: String(value) }); },
  changeBudget(e) {
    const settings = this.data.settings; const current = this.normalizedBudget(); if (current === null) return;
    const delta = Number(e.currentTarget.dataset.delta) * Number(settings.dailyBudgetStep || 1);
    const value = Math.max(Number(settings.dailyBudgetMin), Math.min(Number(settings.dailyBudgetMax), current + delta));
    this.setData({ 'form.dailyBudgetMin': value, dailyBudgetInput: String(value) });
  },
  normalizedBudget() {
    const settings = this.data.settings; const value = Number(this.data.dailyBudgetInput);
    if (!Number.isFinite(value) || !Number.isInteger(value) || value < Number(settings.dailyBudgetMin) || value > Number(settings.dailyBudgetMax)) return null;
    return value;
  },
  toggleWeekday(e) { const bit = 1 << Number(e.currentTarget.dataset.value); this.setData({ 'form.weekdaysMask': this.data.form.weekdaysMask ^ bit }); this.refreshPreview(); },
  toggleTopicPanel() { this.setData({ topicPanelExpanded: !this.data.topicPanelExpanded }); },
  toggleTopic(e) {
    const selected = Object.assign({}, this.data.selectedTopicIds); const id = e.currentTarget.dataset.id; selected[id] = !selected[id];
    const summary = this.topicSummary(this.data.topics, selected);
    this.setData({ selectedTopicIds: selected, selectedTopicCount: summary.count, selectedTopicSummary: summary.text });
  },
  topicSummary(topics, selected) {
    const names = (topics || []).filter((topic) => selected[topic.id]).map((topic) => topic.name);
    return { count: names.length, text: names.length ? names.join('、') : '尚未选择，点击编辑' };
  },
  inputTopic(e) { this.setData({ newTopicName: e.detail.value }); },
  addTopic() {
    const name = (this.data.newTopicName || '').trim(); if (!name) return wx.showToast({ title: '请输入主题名称', icon: 'none' });
    this.setData({ addingTopic: true }); planService.createTopic(name).then((topic) => {
      const ids = Object.assign({}, this.data.selectedTopicIds); ids[topic.id] = true;
      const exists = this.data.topics.some((item) => item.id === topic.id); const topics = exists ? this.data.topics : this.data.topics.concat(topic); const summary = this.topicSummary(topics, ids);
      this.setData({ topics, selectedTopicIds: ids, selectedTopicCount: summary.count, selectedTopicSummary: summary.text, newTopicName: '' });
    }).catch((error) => wx.showToast({ title: error.message || '添加失败', icon: 'none' })).finally(() => this.setData({ addingTopic: false }));
  },
  selectDifficulty(e) { this.setData({ 'form.difficulty': e.currentTarget.dataset.value }); },
  maximumFor(key) { const names = { techCount: 'techCountMax', newWordCount: 'newWordCountMax', reviewLimit: 'reviewLimitMax' }; return Number(this.data.settings[names[key]] || 0); },
  changeCount(e) {
    const key = e.currentTarget.dataset.key; const current = Number(this.data.form[key]); const delta = Number(e.currentTarget.dataset.delta);
    const safeCurrent = Number.isFinite(current) ? current : 0; const safeDelta = Number.isFinite(delta) ? delta : 0;
    this.setData({ [`form.${key}`]: Math.max(0, Math.min(this.maximumFor(key), safeCurrent + safeDelta)) });
  },
  inputCount(e) {
    const key = e.currentTarget.dataset.key; const value = Number(e.detail.value);
    this.setData({ [`form.${key}`]: Number.isFinite(value) ? Math.max(0, Math.min(this.maximumFor(key), Math.floor(value))) : 0 });
  },
  toggleJournal(e) { this.setData({ 'form.journalEnabled': e.detail.value }); },
  toggleReview(e) { this.setData({ 'form.reviewEnabled': e.detail.value }); },
  togglePause() { this.setData({ 'form.paused': !this.data.form.paused }); this.refreshPreview(); },
  refreshPreview() { const d = new Date(); if (this.data.mode !== 'today') d.setDate(d.getDate() + 1); const mask = this.data.form.weekdaysMask; const active = !this.data.form.paused && (mask & (1 << ((d.getDay() + 6) % 7))); this.setData({ isWeekdaySelected: this.weekdayMap(mask), previewDateText: `${d.getMonth() + 1}月${d.getDate()}日`, previewText: active ? '活动日' : '非活动日(不自动安排任务)' }); },
  weekdayMap(mask) { const result = {}; this.data.weekdays.forEach((day) => { result[day.value] = !!(mask & (1 << Number(day.value))); }); return result; },
  adjustToday() { this.submit(true); },
  savePlan() { this.submit(false); },
  submit(adjustToday, budgetConfirmed) {
    const budget = this.normalizedBudget(); const settings = this.data.settings;
    if (budget === null) return wx.showToast({ title: `每日时间请输入 ${settings.dailyBudgetMin}-${settings.dailyBudgetMax} 的整数`, icon: 'none' });
    const form = Object.assign({}, this.data.form, { dailyBudgetMin: budget }); const topicIds = Object.keys(this.data.selectedTopicIds).filter((id) => this.data.selectedTopicIds[id]); const payload = Object.assign({}, form, { topicIds, adjustToday, changeReason: adjustToday ? '用户明确调整今日' : '计划设置保存' });
    const estimatedSeconds = Number(form.techCount || 0) * Number(settings.techEstimateSeconds || 0) + Number(form.newWordCount || 0) * Number(settings.wordEstimateSeconds || 0)
      + (form.journalEnabled ? Number(settings.journalEstimateSeconds || 0) : 0) + (form.reviewEnabled ? Number(form.reviewLimit || 0) * Number(settings.reviewEstimateSeconds || 0) : 0);
    if (!budgetConfirmed && estimatedSeconds > budget * 60) {
      const estimatedMinutes = Math.ceil(estimatedSeconds / 60);
      wx.showModal({ title: '时间预算不足', content: `按当前数量上限预计需要 ${estimatedMinutes} 分钟，你设置了 ${budget} 分钟。系统将按“行动→复盘→到期复习→技术→新词”优先级只安排一部分。`, confirmText: '仍按预算', cancelText: '返回调整', success: (result) => { if (result.confirm) this.submit(adjustToday, true); } });
      return;
    }
    this.setData({ saving: true, 'form.dailyBudgetMin': budget, dailyBudgetInput: String(budget), errorMessage: '', successMessage: '' }); planService.update(payload).then((plan) => {
      this.setData({ form: Object.assign({}, form, { versionNo: plan.versionNo, paused: plan.paused, pauseUntil: plan.pauseUntil }), successMessage: adjustToday ? '今日未开始任务已更新' : '计划已保存，明日生效' });
      this.refreshPreview(); setTimeout(() => { this.setData({ successMessage: '' }); if (adjustToday && getCurrentPages().length > 1) wx.navigateBack(); }, 1200);
    }).catch((error) => { if (error.code === 'PLAN_VERSION_CONFLICT') { this.load(); wx.showToast({ title: '计划已更新，已为你刷新最新内容', icon: 'none' }); } else { this.setData({ errorMessage: error.message || '保存失败，请重试' }); } }).finally(() => this.setData({ saving: false }));
  }
});
