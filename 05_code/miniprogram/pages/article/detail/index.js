const { contentService, authService } = require('../../../services/index');
const FOLLOW_CONSENT_KEY = 'follow_recording_consent_v1';

Page({
  data: { loading: true, acting: false, audioLoading: false, wordAudioLoading: false, error: '', contentId: '', taskId: '', taskVersion: 1, detail: null, showTranslation: true, publishedDate: '', speeds: [0.75, 1, 1.25, 1.5], speed: 1, articlePlaying: false, selectedWord: null, recording: false, recordPath: '', recordUrl: '', recordPlaying: false, recordUploading: false, recordSaved: false, recordDurationMs: 0 },
  onLoad(options) {
    this.articleAudio = wx.createInnerAudioContext(); this.wordAudio = wx.createInnerAudioContext(); this.recordAudio = wx.createInnerAudioContext();
    [this.articleAudio, this.wordAudio, this.recordAudio].forEach((audio) => { audio.obeyMuteSwitch = false; });
    this.articleAudio.onEnded(() => this.setData({ articlePlaying: false })); this.articleAudio.onStop(() => this.setData({ articlePlaying: false }));
    this.recordAudio.onEnded(() => this.setData({ recordPlaying: false })); this.recordAudio.onStop(() => this.setData({ recordPlaying: false }));
    this.recorder = wx.getRecorderManager(); this.recorder.onStop((result) => this.handleRecordingStopped(result));
    this.recorder.onError((error) => { this.setData({ recording: false }); wx.showToast({ title: error.errMsg || '录音失败', icon: 'none' }); });
    this.setData({ contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1 }); this.load(); this.loadFollowRecording();
  },
  onUnload() { [this.articleAudio, this.wordAudio, this.recordAudio].forEach((audio) => audio && audio.destroy()); if (this.data.recording && this.recorder) this.recorder.stop(); },
  load() { this.setData({ loading: true, error: '' }); contentService.get(this.data.contentId).then((detail) => { if (detail.contentType !== 'english_article') throw new Error('该内容不是英语短文'); this.setData({ detail, loading: false, publishedDate: detail.originPublishedAt ? String(detail.originPublishedAt).slice(0, 10) : '' }); }).catch((error) => this.setData({ loading: false, error: error.message || '短文加载失败' })); },
  loadFollowRecording() { contentService.followRecording(this.data.contentId).then((recording) => { if (recording && recording.exists) this.setData({ recordUrl: recording.previewUrl || '', recordSaved: true, recordDurationMs: recording.durationMs || 0 }); }).catch(() => {}); },
  toggleTranslation(event) { this.setData({ showTranslation: !!event.detail.value }); },
  openWord(event) { const id = event.currentTarget.dataset.id; if (id) wx.navigateTo({ url: `/pages/word/detail/index?id=${encodeURIComponent(id)}&returnTo=article` }); },
  selectWord(event) { const block = this.data.detail.articleBlocks[Number(event.currentTarget.dataset.block)]; const token = block && block.tokens[Number(event.currentTarget.dataset.token)]; if (!token || !token.word) return; this.setData({ selectedWord: token }); },
  closeWord() { this.setData({ selectedWord: null }); },
  noop() {},
  changeSpeed(event) { const speed = Number(event.currentTarget.dataset.speed) || 1; this.setData({ speed }); if (this.articleAudio) this.articleAudio.playbackRate = speed; },
  playArticle() {
    if (this.data.audioLoading) return;
    if (this.data.articlePlaying) { this.articleAudio.pause(); this.setData({ articlePlaying: false }); return; }
    const url = this.data.detail.articleAudioUrl;
    if (url) return this.startArticleAudio(url);
    this.setData({ audioLoading: true }); contentService.speech(this.data.contentId).then((result) => { this.setData({ audioLoading: false, 'detail.articleAudioUrl': result.audioUrl }); this.startArticleAudio(result.audioUrl); }).catch((error) => { this.setData({ audioLoading: false }); wx.showToast({ title: error.message || '音频生成失败', icon: 'none', duration: 3000 }); });
  },
  startArticleAudio(url) { this.articleAudio.stop(); this.articleAudio.src = url; this.articleAudio.playbackRate = this.data.speed; this.articleAudio.play(); this.setData({ articlePlaying: true }); },
  playWord() {
    if (this.data.wordAudioLoading) return;
    const word = this.data.selectedWord;
    const target = word && (word.contentId || word.speechKey);
    if (!target) return wx.showToast({ title: '该词暂无词典数据', icon: 'none' });
    if (word.audioUrl) return this.startWordAudio(word.audioUrl);
    this.setData({ wordAudioLoading: true });
    // Token stays on the server; the response contains only the persisted media reference.
    contentService.speech(target).then((result) => {
      const current = this.data.selectedWord;
      if (!current || (current.contentId || current.speechKey) !== target) return;
      this.setData({ 'selectedWord.audioUrl': result.audioUrl });
      this.startWordAudio(result.audioUrl);
    }).catch((error) => wx.showModal({
      title: '暂时无法生成发音', content: error.message || '请稍后重试或联系管理员更新语音配置', showCancel: false
    })).finally(() => this.setData({ wordAudioLoading: false }));
  },
  startWordAudio(url) { this.articleAudio.stop(); this.recordAudio.stop(); this.wordAudio.stop(); this.wordAudio.src = url; this.wordAudio.playbackRate = 1; this.wordAudio.play(); this.setData({ articlePlaying: false, recordPlaying: false }); },
  ensureFollowConsent() { if (wx.getStorageSync(FOLLOW_CONSENT_KEY)) return Promise.resolve(); return new Promise((resolve, reject) => wx.showModal({ title: '保存跟读录音到云端', content: '录音将上传至阿里云 OSS 私有存储，仅本人可访问。同一短文的新录音会替换旧录音，你可随时删除。是否同意？', confirmText: '同意并录音', success: (result) => { if (!result.confirm) { const error = new Error('已取消'); error.cancelled = true; reject(error); return; } authService.consent({ purpose: 'follow_recording_upload', documentVersion: 'FOLLOW_RECORDING_V1', decision: 'grant' }).then(() => { wx.setStorageSync(FOLLOW_CONSENT_KEY, true); resolve(); }).catch(reject); }, fail: reject })); },
  startFollow() { if (this.data.recording || this.data.recordUploading) return; this.ensureFollowConsent().then(() => { this.recordAudio.stop(); this.recorder.start({ duration: 60000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 48000, format: 'mp3' }); this.setData({ recording: true, recordPath: '', recordPlaying: false }); }).catch((error) => { if (!error.cancelled) wx.showToast({ title: error.message || '授权保存失败', icon: 'none' }); }); },
  stopFollow() { if (this.data.recording) this.recorder.stop(); },
  handleRecordingStopped(result) { const path = result.tempFilePath || ''; const durationMs = Math.max(1, Math.min(60000, Math.round(result.duration || 0))); this.setData({ recording: false, recordPath: path, recordPlaying: false, recordUploading: !!path, recordDurationMs: durationMs }); if (!path) return; contentService.uploadFollowRecording(this.data.contentId, path, durationMs).then((saved) => { this.setData({ recordUrl: saved.previewUrl || '', recordSaved: true, recordUploading: false, recordDurationMs: saved.durationMs || durationMs }); wx.showToast({ title: '录音已安全保存', icon: 'success' }); }).catch((error) => { this.setData({ recordUploading: false }); wx.showToast({ title: error.message || '云端保存失败，可重试录制', icon: 'none', duration: 3000 }); }); },
  playRecording() { const url = this.data.recordPath || this.data.recordUrl; if (!url) return; if (this.data.recordPlaying) { this.recordAudio.stop(); return; } this.recordAudio.src = url; this.recordAudio.play(); this.setData({ recordPlaying: true }); },
  deleteRecording() { if (this.data.recordUploading) return; wx.showModal({ title: '删除跟读录音？', content: '云端录音将从你的账号中删除，无法恢复。', confirmText: '删除', confirmColor: '#e64242', success: (result) => { if (!result.confirm) return; if (!this.data.recordSaved) { this.recordAudio.stop(); this.setData({ recordPath: '', recordUrl: '', recordDurationMs: 0 }); return; } this.setData({ recordUploading: true }); contentService.deleteFollowRecording(this.data.contentId).then(() => { this.recordAudio.stop(); this.setData({ recordPath: '', recordUrl: '', recordSaved: false, recordDurationMs: 0 }); wx.showToast({ title: '录音已删除', icon: 'success' }); }).catch((error) => wx.showToast({ title: error.message || '删除失败', icon: 'none' })).finally(() => this.setData({ recordUploading: false })); } }); },
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
