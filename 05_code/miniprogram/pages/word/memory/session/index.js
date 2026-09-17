const { wordMemoryService } = require('../../../../services/index');

Page({
  data: { sessionId: '', loading: true, error: '', session: null, current: null, answer: '', hintContent: '', feedback: null, acting: false },
  onLoad(options) { this.setData({ sessionId: options.id || '' }); this.load(); },
  load() {
    this.setData({ loading: true, error: '' });
    wordMemoryService.getSession(this.data.sessionId).then((session) => {
      if (session.state === 'completed' || session.state === 'partial') return this.openResult();
      const current = session.currentEpisode || null;
      if (current) current.hasClue = (current.availableHints || []).indexOf('clue') >= 0;
      this.startedAt = Date.now(); this.setData({ session, current, answer: '', hintContent: '', feedback: null, loading: false });
    }).catch((error) => this.setData({ loading: false, error: error.message || '训练恢复失败' }));
  },
  answerChanged(event) { this.setData({ answer: event.detail.value }); },
  revealHint(event) {
    if (this.data.acting) return; const type = event.currentTarget.dataset.type; const current = this.data.current;
    this.setData({ acting: true });
    wordMemoryService.revealHint(this.data.sessionId, current.id, { hintType: type, expectedVersion: this.data.session.version })
      .then((result) => { this.setData({ hintContent: result.content, 'session.version': result.sessionVersion }); })
      .catch((error) => this.handleError(error)).finally(() => this.setData({ acting: false }));
  },
  submit() {
    if (this.data.acting) return; const answer = (this.data.answer || '').trim(); if (!answer) return wx.showToast({ title: '请先输入答案', icon: 'none' });
    const current = this.data.current; const attemptNo = (current.attemptCount || 0) + 1; const key = `memory-attempt-${this.data.sessionId}-${current.id}-${attemptNo}`;
    this.setData({ acting: true });
    wordMemoryService.attempt(this.data.sessionId, current.id, { answer, durationMs: Math.max(0, Date.now() - this.startedAt), expectedVersion: this.data.session.version }, key)
      .then((result) => { this.setData({ feedback: result, 'session.version': result.sessionVersion }); })
      .catch((error) => this.handleError(error)).finally(() => this.setData({ acting: false }));
  },
  next() { this.load(); },
  finish() { this.finishWith(false); },
  finishPartial() { wx.showModal({ title: '结束为部分完成？', content: '已提交作答会保留，未测维度不会计为通过。', success: (r) => { if (r.confirm) this.finishWith(true); } }); },
  finishWith(partial) {
    if (this.data.acting) return; this.setData({ acting: true });
    wordMemoryService.finish(this.data.sessionId, { expectedVersion: this.data.session.version, partial })
      .then(() => this.openResult()).catch((error) => this.handleError(error)).finally(() => this.setData({ acting: false }));
  },
  openResult() { wx.redirectTo({ url: `/pages/word/memory/result/index?id=${encodeURIComponent(this.data.sessionId)}` }); },
  handleError(error) { if (error && error.code === 'MEMORY_SESSION_VERSION_CONFLICT') { wx.showToast({ title: '进度已更新，正在恢复', icon: 'none' }); return this.load(); } wx.showToast({ title: (error && error.message) || '操作失败', icon: 'none' }); }
});
