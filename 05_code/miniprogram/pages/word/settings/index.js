const { contentService } = require('../../../services/index');
const { preferenceTypes: defaultCardPreferenceTypes } = require('../../../utils/word-learning-cards');
const studyPreferenceUtils = require('../../../utils/word-study-preferences');

Page({
  data: {
    loading: true,
    error: '',
    studySaving: false,
    cardSaving: false,
    saveMessage: '',
    wordStudyPreferences: studyPreferenceUtils.defaults(),
    studyAccentOptions: studyPreferenceUtils.accentOptions().map(item => item.label),
    studyAccentIndex: 0,
    studyCountOptions: studyPreferenceUtils.countOptions(),
    studyCountIndex: 2,
    studyIntervalOptions: studyPreferenceUtils.intervalOptions().map(item => item.label),
    studyIntervalIndex: 1,
    cardPreferenceTypes: []
  },
  onLoad() { this.load(); },
  onUnload() { if (this.savedMessageTimer) clearTimeout(this.savedMessageTimer); },
  load() {
    this.setData({ loading: true, error: '' });
    Promise.all([
      contentService.wordStudyPreferences(),
      contentService.cardPreferences().catch(() => ({ types: defaultCardPreferenceTypes() }))
    ]).then(([studyPreference, cardPreference]) => {
      this.applyStudyPreferences(studyPreference);
      this.setData({ cardPreferenceTypes: Array.isArray(cardPreference.types) ? cardPreference.types : defaultCardPreferenceTypes(), loading: false });
    }).catch((error) => this.setData({ loading: false, error: error.message || '学习设置加载失败' }));
  },
  changeDefaultHidden(event) { this.saveStudyPreferences({ defaultAnswerMode: event.detail.value ? 'hidden' : 'visible' }); },
  changeAutoPlayEnabled(event) { this.saveStudyPreferences({ autoPlayEnabled: !!event.detail.value }); },
  changeStudyAccent(event) {
    const options = studyPreferenceUtils.accentOptions();
    const selected = options[Number(event.detail.value)] || options[0];
    this.saveStudyPreferences({ autoPlayAccent: selected.value });
  },
  changeStudyCount(event) {
    const options = studyPreferenceUtils.countOptions();
    this.saveStudyPreferences({ autoPlayCount: options[Number(event.detail.value)] || 3 });
  },
  changeStudyInterval(event) {
    const options = studyPreferenceUtils.intervalOptions();
    const selected = options[Number(event.detail.value)] || options[1];
    this.saveStudyPreferences({ autoPlayIntervalMs: selected.value });
  },
  saveStudyPreferences(patch) {
    if (this.data.studySaving) return;
    const before = studyPreferenceUtils.normalize(this.data.wordStudyPreferences);
    const next = studyPreferenceUtils.normalize(Object.assign({}, before, patch));
    this.applyStudyPreferences(next);
    this.setData({ studySaving: true });
    contentService.updateWordStudyPreferences(next).then((saved) => {
      this.applyStudyPreferences(saved);
      this.setData({ studySaving: false });
      this.markSaved('新词学习设置已保存');
    }).catch((error) => {
      this.applyStudyPreferences(before);
      this.setData({ studySaving: false });
      wx.showToast({ title: error.message || '新词学习设置保存失败', icon: 'none' });
    });
  },
  applyStudyPreferences(preference) {
    const value = studyPreferenceUtils.normalize(preference);
    const accentIndex = studyPreferenceUtils.accentOptions().findIndex(item => item.value === value.autoPlayAccent);
    const countIndex = studyPreferenceUtils.countOptions().indexOf(value.autoPlayCount);
    const intervalIndex = studyPreferenceUtils.intervalOptions().findIndex(item => item.value === value.autoPlayIntervalMs);
    this.setData({
      wordStudyPreferences: value,
      studyAccentIndex: Math.max(0, accentIndex),
      studyCountIndex: Math.max(0, countIndex),
      studyIntervalIndex: Math.max(0, intervalIndex)
    });
  },
  changeCardPreference(event) {
    if (this.data.cardSaving) return;
    const type = event.currentTarget.dataset.type;
    const enabled = !!event.detail.value;
    const before = this.data.cardPreferenceTypes || [];
    const next = before.map(item => item.type === type ? Object.assign({}, item, { enabled }) : item);
    const enabledTypes = next.filter(item => item.enabled).map(item => item.type);
    this.setData({ cardPreferenceTypes: next, cardSaving: true });
    contentService.updateCardPreferences({ enabledTypes }).then((preference) => {
      this.setData({ cardPreferenceTypes: Array.isArray(preference.types) ? preference.types : next, cardSaving: false });
      this.markSaved('学习卡片展示已保存');
    }).catch((error) => {
      this.setData({ cardPreferenceTypes: before, cardSaving: false });
      wx.showToast({ title: error.message || '学习卡片展示保存失败', icon: 'none' });
    });
  },
  markSaved(message) {
    if (this.savedMessageTimer) clearTimeout(this.savedMessageTimer);
    this.setData({ saveMessage: message });
    this.savedMessageTimer = setTimeout(() => this.setData({ saveMessage: '' }), 1800);
  }
});
