const { contentService } = require('../../../services/index');

Page({
  data: { loading: true, error: '', detail: null, contentId: '', taskId: '', taskVersion: 1, acting: false, displayExample: '', displayExampleTranslation: '', familiarityText: '未设置' },
  onLoad(options) { this.setData({ contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1 }); this.load(); },
  load() { contentService.get(this.data.contentId).then((detail) => this.setDetail(detail)).catch((error) => this.setData({ loading: false, error: error.message || '词条加载失败' })); },
  play() { wx.showToast({ title: '该词条暂未配置音频', icon: 'none' }); },
  toggleWordBook() { this.act(() => contentService.wordBook(this.data.contentId, !this.data.detail.inWordBook), '生词本已更新'); },
  toggleReview() { this.act(() => contentService.review(this.data.contentId, !this.data.detail.inReview), '复习计划已更新'); },
  understood() { this.act(() => contentService.understood(this.data.contentId, { taskId: this.data.taskId || null, expectedVersion: this.data.taskVersion }), '已记录为理解'); },
  explain() { wx.showModal({ title: 'AI 解释', content: 'AI 入口尚未获得发送授权，本页不会静默发送词条内容。', showCancel: false }); },
  act(action, message) { if (this.data.acting) return; this.setData({ acting: true }); action().then((detail) => { this.setDetail(detail); wx.showToast({ title: message, icon: 'success' }); }).catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false })); },
  setDetail(detail) {
    const firstSense = (detail.senses || [])[0] || {};
    const firstExample = (firstSense.examples || [])[0] || {};
    this.setData({
      detail,
      displayExample: detail.exampleText || firstExample.sentence || '暂无例句',
      displayExampleTranslation: detail.exampleTranslation || firstExample.translation || '',
      familiarityText: detail.familiarityPercent == null ? '未设置' : `${detail.familiarityPercent}%`,
      loading: false,
      error: ''
    });
  }
});
