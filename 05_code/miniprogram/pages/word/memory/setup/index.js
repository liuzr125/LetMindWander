const { wordMemoryService } = require('../../../../services/index');

Page({
  data: {
    returnTo: '', meaning: true, spelling: true, addToReview: false, acting: false,
    loading: true, error: '', source: 'today', sources: { todayLearnedCount: 0, currentBookLearnedCount: 0, maxBatchSize: 5 }
  },
  onLoad(options) {
    this.createKey = `memory-session-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    this.setData({ returnTo: decodeURIComponent(options.returnTo || '') });
    this.loadSources();
  },
  loadSources() {
    this.setData({ loading: true, error: '' });
    wordMemoryService.sources().then((sources) => this.setData({ sources, loading: false }))
      .catch((error) => this.setData({ loading: false, error: error.message || '训练范围加载失败' }));
  },
  selectSource(event) {
    const source = event.currentTarget.dataset.value;
    const count = source === 'today' ? Number(this.data.sources.todayLearnedCount) : Number(this.data.sources.currentBookLearnedCount);
    if (!count) return wx.showToast({ title: source === 'today' ? '今天还没有可测试的单词' : (this.data.sources.currentBookId ? '当前词书还没有学过的单词' : '请先选择学习词书'), icon: 'none' });
    this.setData({ source });
  },
  toggleDimension(event) { const key = event.currentTarget.dataset.key; this.setData({ [key]: !this.data[key] }); },
  toggleReview(event) { this.setData({ addToReview: event.detail.value }); },
  start() {
    if (this.data.acting || this.data.loading) return;
    const available = this.data.source === 'today' ? Number(this.data.sources.todayLearnedCount) : Number(this.data.sources.currentBookLearnedCount);
    if (!available) return wx.showToast({ title: this.data.source === 'today' ? '今天还没有可测试的单词' : '当前词书还没有可测试的单词', icon: 'none' });
    const dimensions = [];
    if (this.data.meaning) dimensions.push('meaning');
    if (this.data.spelling) dimensions.push('spelling');
    if (!dimensions.length) return wx.showToast({ title: '至少选择一个训练维度', icon: 'none' });
    this.setData({ acting: true });
    wordMemoryService.createSession({ dimensions, source: this.data.source, returnTo: this.data.returnTo || null, addToReview: this.data.addToReview }, this.createKey)
      .then((session) => wx.redirectTo({ url: `/pages/word/memory/session/index?id=${encodeURIComponent(session.sessionId)}` }))
      .catch((error) => wx.showModal({ title: '无法开始训练', content: error.message || '请稍后重试', showCancel: false }))
      .finally(() => this.setData({ acting: false }));
  }
});
