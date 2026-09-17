const { weeklySummaryService } = require('../../../services/index');

Page({
  data: { loading: true, saving: false, error: '', weekStart: '', plan: null, editing: null, dateMin: '', dateMax: '', minuteOptions: [1, 2, 3, 5, 8, 10, 15, 20, 30] },
  onLoad(options) { this.setData({ weekStart: options.weekStart || '' }); this.load(); },
  load() {
    this.setData({ loading: true, error: '' });
    weeklySummaryService.actions(this.data.weekStart).then((plan) => this.apply(plan))
      .catch((error) => this.setData({ loading: false, error: error.message || '行动计划加载失败' }));
  },
  apply(plan) { this.setData({ plan, dateMin: plan.weekStart, dateMax: plan.weekEnd, loading: false, saving: false, error: '', editing: null }); },
  add() {
    if (this.data.plan.actions.length >= this.data.plan.maxActions) return;
    this.setData({ editing: { id: '', title: '', note: '', scheduledDate: this.data.plan.weekStart, estimatedMinutes: 3, expectedVersion: null } });
  },
  edit(event) { const action = this.data.plan.actions[event.currentTarget.dataset.index]; if (action.state !== 'draft') return; this.setData({ editing: Object.assign({}, action, { expectedVersion: action.versionNo }) }); },
  inputTitle(event) { this.setData({ 'editing.title': event.detail.value }); },
  inputNote(event) { this.setData({ 'editing.note': event.detail.value }); },
  changeDate(event) { this.setData({ 'editing.scheduledDate': event.detail.value }); },
  changeMinutes(event) { this.setData({ 'editing.estimatedMinutes': this.data.minuteOptions[Number(event.detail.value)] }); },
  cancelEdit() { this.setData({ editing: null }); },
  save() {
    if (this.data.saving) return;
    this.setData({ saving: true });
    weeklySummaryService.saveAction(this.data.weekStart, this.data.editing).then((plan) => { this.apply(plan); wx.showToast({ title: '行动已保存', icon: 'success' }); })
      .catch((error) => { this.setData({ saving: false }); if (error.code === 'ACTION_VERSION_CONFLICT') this.load(); wx.showToast({ title: error.message || '保存失败', icon: 'none' }); });
  },
  remove(event) {
    const action = this.data.plan.actions[event.currentTarget.dataset.index];
    wx.showModal({ title: '移除这项行动？', content: '只会移除尚未确认的行动。', success: (result) => { if (!result.confirm) return; weeklySummaryService.removeAction(this.data.weekStart, action.id, action.versionNo).then((plan) => this.apply(plan)).catch((error) => wx.showToast({ title: error.message || '移除失败', icon: 'none' })); } });
  },
  confirm() {
    if (this.data.saving) return;
    const run = (allow) => { this.setData({ saving: true }); weeklySummaryService.confirmActions(this.data.weekStart, allow).then((plan) => { this.apply(plan); wx.showToast({ title: '已加入目标日期', icon: 'success' }); }).catch((error) => { this.setData({ saving: false }); wx.showToast({ title: error.message || '确认失败', icon: 'none' }); }); };
    if (this.data.plan.containsNonActiveDay) wx.showModal({ title: '包含非学习日', content: '确认后会在对应日期启用临时学习计划，不改变未来常规学习日。', confirmText: '确认启用', success: (result) => { if (result.confirm) run(true); } }); else run(false);
  }
});
