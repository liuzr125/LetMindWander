const { contentService, dailyTaskService, wordMemoryService } = require('../../../services/index');
const { formatDate } = require('../../../utils/date');

Page({
  data: { loading: true, error: '', detail: null, contentId: '', taskId: '', taskVersion: 1, acting: false, displayExample: '', displayExampleTranslation: '', familiarityText: '未设置', memoryEnabled: false, memoryHints: [], memoryHintOpen: true },
  onLoad(options) { this.setData({ contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1 }); this.load(); this.loadWordQueue(); },
  onUnload() { this.stopAudio(); },
  onHide() { this.stopAudio(); },
  load() { contentService.get(this.data.contentId).then((detail) => { this.setDetail(detail); this.loadMemory(); }).catch((error) => this.setData({ loading: false, error: error.message || '词条加载失败' })); },
  loadMemory() { wordMemoryService.config().then((config) => { this.setData({ memoryEnabled: !!config.enabled }); if (config.enabled) return wordMemoryService.hints(this.data.contentId).then((memoryHints) => this.setData({ memoryHints: memoryHints || [] })); }).catch(() => this.setData({ memoryEnabled: false, memoryHints: [] })); },
  toggleMemoryHint() { this.setData({ memoryHintOpen: !this.data.memoryHintOpen }); },
  startMemory() {
    const returnTo = `/pages/word/detail/index?id=${encodeURIComponent(this.data.contentId)}${this.data.taskId ? `&taskId=${encodeURIComponent(this.data.taskId)}&version=${this.data.taskVersion}` : ''}`;
    wx.navigateTo({ url: `/pages/word/memory/setup/index?contentId=${encodeURIComponent(this.data.contentId)}&taskId=${encodeURIComponent(this.data.taskId || '')}&returnTo=${encodeURIComponent(returnTo)}` });
  },
  loadWordQueue() { dailyTaskService.day(formatDate(new Date())).then((pack) => { this.wordQueue = (pack.tasks || []).filter((t) => t.taskType === 'word' && t.status !== 'CANCELLED'); }).catch(() => { this.wordQueue = []; }); },
  play(e) {
    const accent = (e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.accent) || 'uk';
    const url = this.findAudioUrl(accent);
    if (!url) { wx.showToast({ title: '该词条暂未配置音频', icon: 'none' }); return; }
    this.stopAudio(); const ctx = wx.createInnerAudioContext(); this.audioContext = ctx;
    ctx.src = url;
    ctx.play();
    ctx.onError(() => { wx.showToast({ title: '音频播放失败', icon: 'none' }); this.stopAudio(); });
    ctx.onEnded(() => this.stopAudio());
  },
  findAudioUrl(accent) {
    const common = ((this.data.detail && this.data.detail.pronunciations) || []).find((p) => p.accent === accent && p.audioUrl);
    if (common) return common.audioUrl;
    const senses = (this.data.detail && this.data.detail.senses) || [];
    for (const sense of senses) {
      const hit = (sense.pronunciations || []).find((p) => p.accent === accent && p.audioUrl);
      if (hit) return hit.audioUrl;
    }
    return '';
  },
  playExample(event) { const senseIndex=Number(event.currentTarget.dataset.sense),exampleIndex=Number(event.currentTarget.dataset.example);const example=((this.data.detail.senses||[])[senseIndex]||{}).examples||[];const target=example[exampleIndex]||{};const hit=(target.pronunciations||[]).find((p)=>p.audioUrl);if(!hit)return wx.showToast({title:'该例句暂未配置音频',icon:'none'});this.stopAudio();const ctx=wx.createInnerAudioContext();this.audioContext=ctx;ctx.src=hit.audioUrl;ctx.play();ctx.onError(()=>{wx.showToast({title:'音频播放失败',icon:'none'});this.stopAudio();});ctx.onEnded(()=>this.stopAudio()); },
  playDisplayExample() {
    const senses = (this.data.detail && this.data.detail.senses) || [];
    const examples = [];
    senses.forEach((sense) => (sense.examples || []).forEach((example) => examples.push(example)));
    const displayed = this.data.displayExample;
    const target = examples.find((example) => example.sentence === displayed && (example.pronunciations || []).some((item) => item.audioUrl));
    const pronunciation = target && (target.pronunciations || []).find((item) => item.audioUrl);
    if (!pronunciation) return wx.showToast({ title: '该例句暂未配置音频', icon: 'none' });
    this.stopAudio();
    const ctx = wx.createInnerAudioContext(); this.audioContext = ctx; ctx.src = pronunciation.audioUrl; ctx.play();
    ctx.onError(() => { wx.showToast({ title: '音频播放失败', icon: 'none' }); this.stopAudio(); });
    ctx.onEnded(() => this.stopAudio());
  },
  stopAudio(){if(this.audioContext){try{this.audioContext.stop();this.audioContext.destroy();}catch(error){}this.audioContext=null;}},
  toggleWordBook() { this.act(() => contentService.wordBook(this.data.contentId, !this.data.detail.inWordBook), '生词本已更新', false); },
  toggleReview() { this.act(() => contentService.review(this.data.contentId, !this.data.detail.inReview), '复习计划已更新', false); },
  understood() { this.act(() => contentService.understood(this.data.contentId, { taskId: this.data.taskId || null, expectedVersion: this.data.taskVersion }), '', true); },
  familiarityChanged(event) { const value=Number(event.detail.value);this.act(()=>contentService.familiarity(this.data.contentId,value,this.data.detail.recordVersion||0),'熟悉度已保存',false); },
  explain() { wx.showModal({ title: 'AI 解释', content: 'AI 入口尚未获得发送授权，本页不会静默发送词条内容。', showCancel: false }); },
  act(action, message, advance) { if (this.data.acting) return; this.setData({ acting: true }); action().then((detail) => { this.setDetail(detail); if (message) wx.showToast({ title: message, icon: 'success' }); if(advance)setTimeout(() => this.advanceToNext(), 600); }).catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false })); },
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
