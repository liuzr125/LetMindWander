const { contentService } = require('../../../services/index');

Page({
  data: { loading: true, acting: false, audioLoading: false, error: '', contentId: '', taskId: '', taskVersion: 1, detail: null, showTranslation: true, publishedDate: '', speeds: [0.75, 1, 1.25, 1.5], speed: 1, articlePlaying: false, selectedWord: null, recording: false, recordPath: '', recordPlaying: false },
  onLoad(options) {
    this.articleAudio = wx.createInnerAudioContext(); this.wordAudio = wx.createInnerAudioContext(); this.recordAudio = wx.createInnerAudioContext();
    this.articleAudio.onEnded(() => this.setData({ articlePlaying: false })); this.articleAudio.onStop(() => this.setData({ articlePlaying: false }));
    this.recordAudio.onEnded(() => this.setData({ recordPlaying: false })); this.recordAudio.onStop(() => this.setData({ recordPlaying: false }));
    this.recorder = wx.getRecorderManager(); this.recorder.onStop((result) => this.setData({ recording: false, recordPath: result.tempFilePath || '' }));
    this.recorder.onError((error) => { this.setData({ recording: false }); wx.showToast({ title: error.errMsg || '录音失败', icon: 'none' }); });
    this.setData({ contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1 }); this.load();
  },
  onUnload() { [this.articleAudio, this.wordAudio, this.recordAudio].forEach((audio) => audio && audio.destroy()); if (this.data.recording && this.recorder) this.recorder.stop(); },
  load() { this.setData({ loading: true, error: '' }); contentService.get(this.data.contentId).then((detail) => { if (detail.contentType !== 'english_article') throw new Error('该内容不是英语短文'); this.setData({ detail, loading: false, publishedDate: detail.originPublishedAt ? String(detail.originPublishedAt).slice(0, 10) : '' }); }).catch((error) => this.setData({ loading: false, error: error.message || '短文加载失败' })); },
  toggleTranslation(event) { this.setData({ showTranslation: !!event.detail.value }); },
  openWord(event) { const id = event.currentTarget.dataset.id; if (id) wx.navigateTo({ url: `/pages/word/detail/index?id=${encodeURIComponent(id)}&returnTo=article` }); },
  selectWord(event) { const block = this.data.detail.articleBlocks[Number(event.currentTarget.dataset.block)]; const token = block && block.tokens[Number(event.currentTarget.dataset.token)]; if (!token || !token.word) return; this.setData({ selectedWord: token }); },
  closeWord() { this.setData({ selectedWord: null }); },
  noop() {},
  changeSpeed(event) { const speed = Number(event.currentTarget.dataset.speed) || 1; this.setData({ speed }); if (this.articleAudio) this.articleAudio.playbackRate = speed; },
  playArticle() {
    if (this.data.articlePlaying) { this.articleAudio.pause(); this.setData({ articlePlaying: false }); return; }
    const url = this.data.detail.articleAudioUrl;
    if (url) return this.startArticleAudio(url);
    this.setData({ audioLoading: true }); contentService.speech(this.data.contentId).then((result) => { this.setData({ audioLoading: false, 'detail.articleAudioUrl': result.audioUrl }); this.startArticleAudio(result.audioUrl); }).catch((error) => { this.setData({ audioLoading: false }); wx.showToast({ title: error.message || '音频生成失败', icon: 'none', duration: 3000 }); });
  },
  startArticleAudio(url) { this.articleAudio.src = url; this.articleAudio.playbackRate = this.data.speed; this.articleAudio.play(); this.setData({ articlePlaying: true }); },
  playWord() { const word = this.data.selectedWord; const target = word && (word.contentId || word.speechKey); if (!target) return wx.showToast({ title: '该词暂无词典数据', icon: 'none' }); if (word.audioUrl) return this.startWordAudio(word.audioUrl); contentService.speech(target).then((result) => { this.setData({ 'selectedWord.audioUrl': result.audioUrl }); this.startWordAudio(result.audioUrl); }).catch((error) => wx.showToast({ title: error.message || '单词发音失败', icon: 'none' })); },
  startWordAudio(url) { this.wordAudio.stop(); this.wordAudio.src = url; this.wordAudio.playbackRate = 1; this.wordAudio.play(); },
  startFollow() { if (this.data.recording) return; this.recordAudio.stop(); this.recorder.start({ duration: 60000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 48000, format: 'mp3' }); this.setData({ recording: true, recordPath: '', recordPlaying: false }); },
  stopFollow() { if (this.data.recording) this.recorder.stop(); },
  playRecording() { if (!this.data.recordPath) return; if (this.data.recordPlaying) { this.recordAudio.stop(); return; } this.recordAudio.src = this.data.recordPath; this.recordAudio.play(); this.setData({ recordPlaying: true }); },
  toggleFavorite() { this.act(() => contentService.favorite(this.data.contentId, !this.data.detail.favorite), '收藏状态已更新'); },
  understood() { this.act(() => contentService.understood(this.data.contentId, { taskId: this.data.taskId || null, expectedVersion: this.data.taskVersion }), '已记录为理解'); },
  takeNote() { const detail = this.data.detail; if (!detail) return; wx.navigateTo({ url: `/pages/knowledge/editor/index?sourceContentId=${encodeURIComponent(detail.contentId)}&sourceContentVersionId=${encodeURIComponent(detail.versionId)}` }); },
  explain(event) {
    const block = this.data.detail && this.data.detail.articleBlocks[Number(event.currentTarget.dataset.block)];
    const paragraph = block && (block.text || (block.tokens || []).map((token) => token.text).join(''));
    if (!paragraph) return wx.showToast({ title: '当前段落为空', icon: 'none' });
    wx.setStorageSync('pendingAiQuestion', {
      question: `请用简洁中文解释下面这段英语，说明关键词汇、语法结构和自然译文：\n\n${paragraph}`,
      autoSend: true
    });
    wx.navigateTo({ url: '/pages/ai/ask/index?source=article' });
  },
  act(action, message) { if (this.data.acting) return; this.setData({ acting: true }); action().then((detail) => { this.setData({ detail }); wx.showToast({ title: message, icon: 'success' }); }).catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false })); }
});
