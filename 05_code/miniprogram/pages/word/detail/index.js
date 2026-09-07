const { contentService, dailyTaskService } = require('../../../services/index');
const { formatDate } = require('../../../utils/date');

Page({
  data: { loading: true, error: '', detail: null, contentId: '', taskId: '', taskVersion: 1, acting: false, displayExample: '', displayExampleTranslation: '', familiarityText: '未设置' },
  onLoad(options) { this.setData({ contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1 }); this.load(); this.loadWordQueue(); },
  load() { contentService.get(this.data.contentId).then((detail) => this.setDetail(detail)).catch((error) => this.setData({ loading: false, error: error.message || '词条加载失败' })); },
  loadWordQueue() { dailyTaskService.day(formatDate(new Date())).then((pack) => { this.wordQueue = (pack.tasks || []).filter((t) => t.taskType === 'word' && t.status !== 'CANCELLED'); }).catch(() => { this.wordQueue = []; }); },
  play(e) {
    const accent = (e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.accent) || 'uk';
    const url = this.findAudioUrl(accent);
    if (!url) { wx.showToast({ title: '该词条暂未配置音频', icon: 'none' }); return; }
    const ctx = wx.createInnerAudioContext();
    ctx.src = url;
    ctx.play();
    ctx.onError(() => { wx.showToast({ title: '音频播放失败', icon: 'none' }); ctx.destroy(); });
    ctx.onEnded(() => ctx.destroy());
  },
  findAudioUrl(accent) {
    const senses = (this.data.detail && this.data.detail.senses) || [];
    for (const sense of senses) {
      const hit = (sense.pronunciations || []).find((p) => p.accent === accent && p.audioUrl);
      if (hit) return hit.audioUrl;
    }
    return '';
  },
  toggleWordBook() { this.act(() => contentService.wordBook(this.data.contentId, !this.data.detail.inWordBook), '生词本已更新'); },
  toggleReview() { this.act(() => contentService.review(this.data.contentId, !this.data.detail.inReview), '复习计划已更新'); },
  understood() { this.act(() => contentService.understood(this.data.contentId, { taskId: this.data.taskId || null, expectedVersion: this.data.taskVersion }), ''); },
  explain() { wx.showModal({ title: 'AI 解释', content: 'AI 入口尚未获得发送授权，本页不会静默发送词条内容。', showCancel: false }); },
  act(action, message) { if (this.data.acting) return; this.setData({ acting: true }); action().then((detail) => { this.setDetail(detail); if (message) wx.showToast({ title: message, icon: 'success' }); setTimeout(() => this.advanceToNext(), 600); }).catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false })); },
  advanceToNext() {
    const queue = this.wordQueue || [];
    const idx = queue.findIndex((t) => t.id === this.data.taskId || t.contentId === this.data.contentId);
    if (idx < 0) return;
    const current = queue[idx];
    if (current && current.status === 'DONE') return;
    const next = queue.slice(idx + 1).find((t) => t.status !== 'DONE' && t.status !== 'SKIPPED');
    if (next) {
      wx.redirectTo({ url: `/pages/word/detail/index?id=${next.contentId}&taskId=${next.id}&version=${next.versionNo}` });
    } else {
      setTimeout(() => wx.navigateBack(), 500);
    }
  },
  setDetail(detail) {
    const firstSense = (detail.senses || [])[0] || {};
    const firstExample = (firstSense.examples || [])[0] || {};
    this.setData({
      detail,
      phoneticText: this.formatPhonetic(detail.phonetic),
      displayExample: detail.exampleText || firstExample.sentence || '暂无例句',
      displayExampleTranslation: detail.exampleTranslation || firstExample.translation || '',
      familiarityText: detail.familiarityPercent == null ? '未设置' : `${detail.familiarityPercent}%`,
      loading: false,
      error: ''
    });
  },
  formatPhonetic(ph) {
    if (!ph) return '';
    let uk = ''; let us = '';
    (ph.split('|')).forEach((part) => {
      const m = part.trim().match(/^(UK|US)[\s:：]*(\S.*)$/);
      if (!m) return;
      if (m[1] === 'UK') uk = m[2]; else us = m[2];
    });
    if (uk && us) return `${uk}（英式发音） | ${us}（美式发音）`;
    if (uk || us) return `${uk || us}（${uk ? '英式发音' : '美式发音'}）`;
    return ph;
  }
});
