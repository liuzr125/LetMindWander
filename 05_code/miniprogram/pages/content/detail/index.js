const { contentService } = require('../../../services/index');

Page({
  data: { loading: true, error: '', detail: null, taskId: '', taskVersion: 1, acting: false },
  onLoad(options) { this.setData({ contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1 }); this.load(); },
  load() { this.setData({ loading: true, error: '' }); contentService.get(this.data.contentId).then((detail) => this.setData({ detail, loading: false })).catch((error) => this.setData({ error: error.message || '内容加载失败', loading: false })); },
  copySource() { const url = this.data.detail && this.data.detail.originUrl; if (!url) return wx.showToast({ title: '暂无可用来源链接', icon: 'none' }); wx.setClipboardData({ data: url }); },
  toggleFavorite() { this.act(() => contentService.favorite(this.data.contentId, !this.data.detail.favorite), '收藏状态已更新'); },
  toggleReview() { this.act(() => contentService.review(this.data.contentId, !this.data.detail.inReview), '复习计划已更新'); },
  understood() { this.act(() => contentService.understood(this.data.contentId, { taskId: this.data.taskId || null, expectedVersion: this.data.taskVersion }), '已记录为理解'); },
  explain() { wx.showModal({ title: 'AI 解释', content: '该入口需要先完成 AI 授权与统一任务队列配置；当前不会发送正文或产生费用。', showCancel: false }); },
  act(action, message) { if (this.data.acting) return; this.setData({ acting: true }); action().then((detail) => { this.setData({ detail }); wx.showToast({ title: message, icon: 'success' }); }).catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false })); }
});
