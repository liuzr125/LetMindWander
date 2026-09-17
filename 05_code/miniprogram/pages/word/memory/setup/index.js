const { wordMemoryService } = require('../../../../services/index');

Page({
  data: { contentId: '', taskId: '', returnTo: '', meaning: true, spelling: true, addToReview: false, acting: false },
  onLoad(options) {
    this.createKey = `memory-session-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    this.setData({ contentId: options.contentId || '', taskId: options.taskId || '', returnTo: decodeURIComponent(options.returnTo || '') });
  },
  toggleDimension(event) { const key = event.currentTarget.dataset.key; this.setData({ [key]: !this.data[key] }); },
  toggleReview(event) { this.setData({ addToReview: event.detail.value }); },
  start() {
    if (this.data.acting) return;
    const dimensions = [];
    if (this.data.meaning) dimensions.push('meaning');
    if (this.data.spelling) dimensions.push('spelling');
    if (!dimensions.length) return wx.showToast({ title: '至少选择一个训练维度', icon: 'none' });
    this.setData({ acting: true });
    wordMemoryService.createSession({ contentIds: [this.data.contentId], dimensions, source: 'word_detail', returnTo: this.data.returnTo || null, taskId: this.data.taskId || null, addToReview: this.data.addToReview }, this.createKey)
      .then((session) => wx.redirectTo({ url: `/pages/word/memory/session/index?id=${encodeURIComponent(session.sessionId)}` }))
      .catch((error) => wx.showModal({ title: '无法开始训练', content: error.message || '请稍后重试', showCancel: false }))
      .finally(() => this.setData({ acting: false }));
  }
});
