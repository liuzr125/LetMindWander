const { contentService, dailyTaskService, wordMemoryService } = require('../../../services/index');
const { formatDate } = require('../../../utils/date');
const { groups: cardGroups, preferenceTypes: defaultCardPreferenceTypes } = require('../../../utils/word-learning-cards');
const studyPreferenceUtils = require('../../../utils/word-study-preferences');
const EXAMPLE_ACCENT_KEY = 'word_example_accent_v1';
const WORD_BROWSE_QUEUE_KEY = 'word_learning_queue_v1';

Page({
  // Independent supplementary UI state; never used as evidence of mastery.
  supplementDefaults: { recallActive: false, learningCardGroups: [], cardsLoading: false, cardsError: '' },
  data: { loading: true, error: '', detail: null, contentId: '', taskId: '', taskVersion: 1, acting: false, displayExample: '', displayExampleTranslation: '', wordSizeClass: '', headerPronunciations: [], hasSenseExamples: false, memoryEnabled: false, memoryHints: [], memoryHintOpen: true, exampleAccent: 'uk', exampleAccentIndex: 0, exampleAccentOptions: ['英式', '美式'], wordStudyPreferences: studyPreferenceUtils.defaults() },
  onLoad(options) { const savedAccent = wx.getStorageSync(EXAMPLE_ACCENT_KEY) === 'us' ? 'us' : 'uk'; this.setData(Object.assign({}, this.supplementDefaults, { contentId: options.id || '', taskId: options.taskId || '', taskVersion: Number(options.version) || 1, exampleAccent: savedAccent, exampleAccentIndex: savedAccent === 'us' ? 1 : 0, memoryHintOpen: false })); this.load(); this.loadWordQueue(); },
  onShow() {
    if (!this.refreshSettingsOnShow) return;
    this.refreshSettingsOnShow = false;
    contentService.wordStudyPreferences().then((preference) => this.applyStudyPreferences(preference, true)).catch(() => {});
    this.loadLearningCards();
  },
  onUnload() { this.stopAudio(); },
  onHide() { this.stopAudio(); },
  load() {
    const preferenceRequest = contentService.wordStudyPreferences().catch(() => studyPreferenceUtils.defaults());
    Promise.all([contentService.get(this.data.contentId), preferenceRequest]).then(([detail, preference]) => {
      this.applyStudyPreferences(preference, true);
      this.setDetail(detail);
      this.loadMemory(); this.loadLearningCards(); this.startAutoPronunciation();
    }).catch((error) => this.setData({ loading: false, error: error.message || '词条加载失败' }));
  },
  openLearningSettings() {
    this.refreshSettingsOnShow = true;
    this.stopAudio();
    wx.navigateTo({ url: '/pages/word/settings/index', fail: () => { this.refreshSettingsOnShow = false; wx.showToast({ title: '设置页面打开失败', icon: 'none' }); } });
  },
  applyStudyPreferences(preference, applyRecall) {
    const value = studyPreferenceUtils.normalize(preference);
    const state = { wordStudyPreferences: value };
    if (applyRecall) state.recallActive = value.defaultAnswerMode === 'hidden';
    this.setData(state);
  },
  loadLearningCards() {
    this.setData({ cardsLoading: true, cardsError: '' });
    const preferenceRequest = contentService.cardPreferences().catch(() => ({ types: defaultCardPreferenceTypes() }));
    Promise.all([preferenceRequest, contentService.learningCards(this.data.contentId)]).then(([preference, cards]) => {
      const types = Array.isArray(preference.types) ? preference.types : defaultCardPreferenceTypes();
      const enabledTypes = types.filter(item => item.enabled).map(item => item.type);
      this.setData({ learningCardGroups: cardGroups(cards, enabledTypes), cardsLoading: false });
    })
      .catch(() => this.setData({ cardsLoading: false, cardsError: '辅助卡片暂未加载，不影响基础学习' }));
  },
  toggleCardGroup(event) {
    const type = event.currentTarget.dataset.type;
    this.setData({ learningCardGroups: (this.data.learningCardGroups || []).map(g => g.type === type ? Object.assign({}, g, { open: !g.open }) : g) });
  },
  expandCardGroup(event) {
    const type = event.currentTarget.dataset.type;
    this.setData({ learningCardGroups: (this.data.learningCardGroups || []).map(g => g.type === type ? Object.assign({}, g, { expanded: !g.expanded, visibleItems: g.expanded ? g.items.slice(0, 1) : g.items }) : g) });
  },
  showCardSource(event) {
    const id = event.currentTarget.dataset.id;
    const card = (this.data.learningCardGroups || []).reduce((all,g) => all.concat(g.items), []).find(c => c.id === id);
    if (!card) return;
    const url = /^https:\/\//i.test(card.sourceUrl || '') ? card.sourceUrl : '';
    wx.showModal({ title: '出处与使用说明', content: [card.sourceTitle, card.sourceLocator, card.sourceLabel, card.rightsNote, url].filter(Boolean).join('\n'),
      showCancel: !!url, confirmText: url ? '复制来源' : '知道了',
      success: result => { if (result.confirm && url) wx.setClipboardData({ data: url }); } });
  },
  toggleRecall() {
    this.stopAudio();
    this.setData({ recallActive: !this.data.recallActive });
    wx.pageScrollTo({ scrollTop: 0, duration: 200 });
  },
  loadMemory() { wordMemoryService.config().then((config) => { this.setData({ memoryEnabled: !!config.enabled }); if (config.enabled) return wordMemoryService.hints(this.data.contentId).then((memoryHints) => this.setData({ memoryHints: memoryHints || [] })); }).catch(() => this.setData({ memoryEnabled: false, memoryHints: [] })); },
  toggleMemoryHint() { this.setData({ memoryHintOpen: !this.data.memoryHintOpen }); },
  loadWordQueue() {
    const cached = wx.getStorageSync(WORD_BROWSE_QUEUE_KEY) || {};
    this.browseWordQueue = Date.now() - Number(cached.createdAt || 0) < 6 * 60 * 60 * 1000 ? (cached.ids || []) : [];
    dailyTaskService.day(formatDate(new Date())).then((pack) => { this.wordQueue = (pack.tasks || []).filter((t) => t.taskType === 'word' && t.status !== 'CANCELLED'); }).catch(() => { this.wordQueue = []; });
  },
  play(e) {
    const accent = (e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.accent) || 'uk';
    const pronunciation = (this.data.headerPronunciations || []).find((item) => item.accent === accent && item.audioUrl);
    this.playAudio(pronunciation && pronunciation.audioUrl, '该词条暂未配置该口音音频', { scope: pronunciation && pronunciation.scope || 'word', senseId: pronunciation && pronunciation.senseId, accent, assetId: pronunciation && pronunciation.assetId });
  },
  startAutoPronunciation() {
    const preference = studyPreferenceUtils.normalize(this.data.wordStudyPreferences);
    if (!preference.autoPlayEnabled) return;
    const pronunciations = this.data.headerPronunciations || [];
    const pronunciation = pronunciations.find(item => item.accent === preference.autoPlayAccent && item.audioUrl) || pronunciations.find(item => item.audioUrl);
    if (!pronunciation) return;
    this.autoPlayStartTimer = setTimeout(() => this.playAudio(pronunciation.audioUrl, '', { scope: pronunciation.scope || 'word', senseId: pronunciation.senseId, accent: pronunciation.accent, assetId: pronunciation.assetId, repeatCount: preference.autoPlayCount, intervalMs: preference.autoPlayIntervalMs, automatic: true }), 250);
  },
  playAudio(url, emptyMessage, diagnostics) {
    if (!url) { wx.showToast({ title: emptyMessage || '暂未配置音频', icon: 'none' }); return; }
    this.stopAudio(); const token = this.audioSequenceToken; const ctx = wx.createInnerAudioContext(); this.audioContext = ctx; ctx.obeyMuteSwitch = false;
    const repeatCount = Math.max(1, Math.min(5, Number(diagnostics && diagnostics.repeatCount) || 1));
    const intervalMs = Math.max(1000, Math.min(2000, Number(diagnostics && diagnostics.intervalMs) || 1500));
    let playsFinished = 0;
    ctx.src = url;
    ctx.onError((error) => {
      let client = {};
      try { const system = wx.getSystemInfoSync(); client = { platform: system.platform, system: system.system, version: system.version, SDKVersion: system.SDKVersion }; } catch (ignored) {}
      console.error('[word-audio-play-failed]', Object.assign({ contentId: this.data.contentId, errMsg: error && error.errMsg, errCode: error && error.errCode, client }, diagnostics || {}));
      wx.showToast({ title: '音频播放失败，请稍后重试', icon: 'none' }); this.stopAudio();
    });
    ctx.onEnded(() => {
      if (this.audioSequenceToken !== token || this.audioContext !== ctx) return;
      playsFinished += 1;
      const delay = studyPreferenceUtils.nextPlaybackDelay(playsFinished, repeatCount, intervalMs);
      if (delay === null) { this.stopAudio(); return; }
      this.audioRepeatTimer = setTimeout(() => {
        if (this.audioSequenceToken !== token || this.audioContext !== ctx) return;
        try { ctx.seek(0); } catch (ignored) {}
        ctx.play();
      }, delay);
    });
    ctx.play();
  },
  playExample(event) {
    const sense = ((this.data.detail && this.data.detail.senses) || [])[Number(event.currentTarget.dataset.sense)] || {};
    const example = (sense.examples || [])[Number(event.currentTarget.dataset.example)] || {};
    const accent = event.currentTarget.dataset.accent;
    const hit = (example.pronunciations || []).find((item) => item.accent === accent && item.audioUrl);
    this.playAudio(hit && hit.audioUrl, '该例句暂未配置该口音音频', { scope: 'example', exampleId: example.id, senseId: sense.id, accent, assetId: hit && hit.assetId });
  },
  changeExampleAccent(event) {
    const index = Number(event.detail.value) === 1 ? 1 : 0; const accent = index === 1 ? 'us' : 'uk';
    wx.setStorageSync(EXAMPLE_ACCENT_KEY, accent);
    this.setData({ exampleAccent: accent, exampleAccentIndex: index, detail: this.withExampleAccent(this.data.detail, accent) });
  },
  stopAudio(){this.audioSequenceToken=(this.audioSequenceToken||0)+1;if(this.autoPlayStartTimer){clearTimeout(this.autoPlayStartTimer);this.autoPlayStartTimer=null;}if(this.audioRepeatTimer){clearTimeout(this.audioRepeatTimer);this.audioRepeatTimer=null;}if(this.audioContext){try{this.audioContext.stop();this.audioContext.destroy();}catch(error){}this.audioContext=null;}},
  toggleWordBook() { this.act(() => contentService.wordBook(this.data.contentId, !this.data.detail.inWordBook), '生词本已更新', false); },
  toggleReview() { this.act(() => contentService.review(this.data.contentId, !this.data.detail.inReview), '复习计划已更新', false); },
  understood() { this.act(() => contentService.understood(this.data.contentId, { taskId: this.data.taskId || null, expectedVersion: this.data.taskVersion }), '', true); },
  rateWord(event) {
    if (this.data.acting || this.data.recallActive) return;
    const feedback = event.currentTarget.dataset.value;
    const task = this.currentWordTask();
    const payload = { feedback, taskId: this.data.taskId || (task && task.id) || null, expectedVersion: this.data.taskId ? this.data.taskVersion : (task && task.versionNo) };
    this.act(() => contentService.feedback(this.data.contentId, payload), '', true);
  },
  currentWordTask() { return (this.wordQueue || []).find((task) => task.id === this.data.taskId || task.contentId === this.data.contentId); },
  familiarityChanged(event) { const value=Number(event.detail.value);this.act(()=>contentService.familiarity(this.data.contentId,value,this.data.detail.recordVersion||0),'熟悉度已保存',false); },
  explain() { wx.showModal({ title: 'AI 解释', content: 'AI 入口尚未获得发送授权，本页不会静默发送词条内容。', showCancel: false }); },
  act(action, message, advance) { if (this.data.acting) return; this.setData({ acting: true }); action().then((detail) => { this.setDetail(detail); if (message) wx.showToast({ title: message, icon: 'success' }); if(advance)setTimeout(() => this.advanceToNext(), 600); }).catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false })); },
  advanceToNext() {
    const queue = this.wordQueue || [];
    const idx = queue.findIndex((t) => t.id === this.data.taskId || t.contentId === this.data.contentId);
    if (idx >= 0) {
      const next = queue.slice(idx + 1).find((t) => t.status !== 'DONE' && t.status !== 'SKIPPED');
      if (next) return wx.redirectTo({ url: `/pages/word/detail/index?id=${next.contentId}&taskId=${next.id}&version=${next.versionNo}` });
    }
    const browseQueue = this.browseWordQueue || [];
    const browseIndex = browseQueue.indexOf(this.data.contentId);
    const nextContentId = browseIndex >= 0 ? browseQueue.slice(browseIndex + 1).find((id) => id && id !== this.data.contentId) : null;
    if (nextContentId) return wx.redirectTo({ url: `/pages/word/detail/index?id=${encodeURIComponent(nextContentId)}&returnTo=learn` });
    setTimeout(() => wx.navigateBack(), 500);
  },
  setDetail(detail) {
    detail = this.withExampleAccent(detail, this.data.exampleAccent);
    const firstSense = (detail.senses || [])[0] || {};
    const firstExample = (firstSense.examples || [])[0] || {};
    const commonPronunciations = (detail.pronunciations || []).filter((item) => item.audioUrl).map((item) => Object.assign({}, item, { scope: 'word' }));
    const sensePronunciations = (firstSense.pronunciations || []).filter((item) => item.audioUrl).map((item) => Object.assign({}, item, { scope: 'sense', senseId: firstSense.id }));
    const headerPronunciations = [];
    commonPronunciations.concat(sensePronunciations).forEach((item) => {
      if (!headerPronunciations.some((current) => current.accent === item.accent)) headerPronunciations.push(Object.assign({}, item, { displayPhonetic: item.phonetic || this.accentPhonetic(detail.phonetic, item.accent) }));
    });
    this.setData({
      detail,
      wordSizeClass: this.wordSizeClass(detail.wordTerm || detail.title),
      headerPronunciations,
      hasSenseExamples: (detail.senses || []).some((sense) => (sense.examples || []).length),
      phoneticText: this.formatPhonetic(detail.phonetic),
      displayExample: detail.exampleText || firstExample.sentence || '暂无例句',
      displayExampleTranslation: detail.exampleTranslation || firstExample.translation || '',
      loading: false,
      error: ''
    });
  },
  withExampleAccent(detail, accent) {
    if (!detail) return detail;
    const senses = (detail.senses || []).map((sense) => Object.assign({}, sense, { examples: (sense.examples || []).map((example) => {
      const available = (example.pronunciations || []).filter((item) => item.audioUrl);
      const selected = available.find((item) => item.accent === accent) || available[0] || null;
      return Object.assign({}, example, { selectedPronunciation: selected, selectedAccent: selected && selected.accent, accentFallback: !!selected && selected.accent !== accent });
    }) }));
    return Object.assign({}, detail, { senses });
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
  },
  accentPhonetic(ph, accent) {
    if (!ph) return '';
    let fallback = '';
    (ph.split('|')).forEach((part) => {
      const value = part.trim();
      const match = value.match(/^(UK|US)[\s:：]*(\S.*)$/);
      if (!match) { if (!fallback) fallback = value; return; }
      if ((accent === 'uk' && match[1] === 'UK') || (accent === 'us' && match[1] === 'US')) fallback = match[2];
    });
    return fallback;
  },
  wordSizeClass(word) {
    const length = Array.from(word || '').length;
    if (length > 16) return 'word-xlong';
    if (length > 12) return 'word-long';
    if (length > 8) return 'word-medium';
    return 'word-regular';
  }
});
