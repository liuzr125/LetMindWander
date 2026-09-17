const { weeklySummaryService } = require('../../../services/index');

Page({
  data: { loading: true, saving: false, error: '', weekStart: '', summary: null, body: '', editing: false, showDays: false },
  onLoad(options) { this.setData({ weekStart: options.weekStart || '' }); this.load(); },
  load() {
    this.setData({ loading: true, error: '' });
    weeklySummaryService.get(this.data.weekStart).then((summary) => this.apply(summary))
      .catch((error) => this.setData({ loading: false, error: error.message || '周总结加载失败' }));
  },
  apply(summary) { this.setData({ summary, body: summary.body || '', loading: false, error: '', editing: false }); },
  toggleDays() { this.setData({ showDays: !this.data.showDays }); },
  startEdit() { if (this.data.summary.status === 'empty') return; this.setData({ editing: true }); },
  cancelEdit() { this.setData({ editing: false, body: this.data.summary.body || '' }); },
  inputBody(event) { this.setData({ body: event.detail.value }); },
  save() {
    if (this.data.saving) return;
    this.setData({ saving: true });
    weeklySummaryService.edit(this.data.weekStart, { body: this.data.body, expectedVersion: this.data.summary.versionNo })
      .then((summary) => { this.apply(summary); wx.showToast({ title: '已保存新修订', icon: 'success' }); })
      .catch((error) => { if (error.code === 'WEEKLY_VERSION_CONFLICT') this.load(); wx.showToast({ title: error.message || '保存失败', icon: 'none' }); })
      .finally(() => this.setData({ saving: false }));
  },
  confirm() {
    if (this.data.saving || !this.data.summary.currentRevisionId) return;
    this.setData({ saving: true });
    weeklySummaryService.confirm(this.data.weekStart, { revisionId: this.data.summary.currentRevisionId, expectedVersion: this.data.summary.versionNo })
      .then((summary) => { this.apply(summary); wx.showToast({ title: '周总结已确认', icon: 'success' }); })
      .catch((error) => { if (error.code === 'WEEKLY_VERSION_CONFLICT') this.load(); wx.showToast({ title: error.message || '确认失败', icon: 'none' }); })
      .finally(() => this.setData({ saving: false }));
  },
  recalculate() {
    if (this.data.saving) return;
    this.setData({ saving: true });
    weeklySummaryService.recalculate(this.data.weekStart).then((summary) => { this.apply(summary); wx.showToast({ title: '已生成新草稿', icon: 'success' }); })
      .catch((error) => wx.showToast({ title: error.message || '重新汇总失败', icon: 'none' })).finally(() => this.setData({ saving: false }));
  },
  openActions() { if (this.data.summary.status !== 'empty') wx.navigateTo({ url: `/pages/action/plan/index?weekStart=${this.data.weekStart}` }); },
  aiUnavailable() { wx.showToast({ title: 'AI 润色需在统一授权流程接入后启用', icon: 'none', duration: 2200 }); }
});
