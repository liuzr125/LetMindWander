const { learningService } = require('../../services/index');

const STORAGE_KEY = 'learning_page_state_v1';

Page({
  data: {
    primaryTab: 'tech', englishTab: 'vocabulary', topicId: '', difficulty: '', stage: '', notebookStatus: '', keyword: '',
    topics: [], difficulties: [], stages: [], notebookStatuses: [], difficultyIndex: 0, stageIndex: 0,
    items: [], notebookSummary: { totalCount: 0, dueCount: 0 }, loading: true, loadingMore: false, error: '', page: 1, hasMore: false
  },
  onLoad() {
    const saved = wx.getStorageSync(STORAGE_KEY) || {};
    this.setData({
      primaryTab: saved.primaryTab === 'english' ? 'english' : 'tech',
      englishTab: ['vocabulary', 'notebook', 'article'].includes(saved.englishTab) ? saved.englishTab : 'vocabulary',
      topicId: saved.topicId || '', difficulty: saved.difficulty || '', stage: saved.stage || '', notebookStatus: saved.notebookStatus || ''
    });
    this.loadFilters().then(() => {
      this.loadTopics(); this.reload(() => {
        if (saved.scrollTop) setTimeout(() => wx.pageScrollTo({ scrollTop: saved.scrollTop, duration: 0 }), 60);
      });
    });
  },
  onShow() {
    if (wx.getStorageSync('learning_deep_link') === 'wordbook') {
      wx.removeStorageSync('learning_deep_link');
      this.setData({ primaryTab: 'english', englishTab: 'notebook', keyword: '' });
      this.remember({ primaryTab: 'english', englishTab: 'notebook' });
      this.reload();
      this.loadNotebookSummary();
    }
  },
  onPullDownRefresh() { this.reload(() => wx.stopPullDownRefresh()); },
  onReachBottom() { if (this.data.hasMore && !this.data.loadingMore) this.load(true); },
  onPageScroll(event) { clearTimeout(this.scrollTimer); this.scrollTimer = setTimeout(() => this.remember({ scrollTop: event.scrollTop }), 120); },
  switchPrimary(event) { const value = event.currentTarget.dataset.value; if (value === this.data.primaryTab) return; this.setData({ primaryTab: value }); this.remember({ primaryTab: value }); this.reload(); },
  switchEnglish(event) { const value = event.currentTarget.dataset.value; if (value === this.data.englishTab) return; this.setData({ englishTab: value, keyword: '' }); this.remember({ englishTab: value }); this.reload(); },
  selectTopic(event) { const value = event.currentTarget.dataset.value || ''; this.setData({ topicId: value }); this.remember({ topicId: value }); this.reload(); },
  changeDifficulty(event) { const index = Number(event.detail.value) || 0; const option = this.data.difficulties[index] || this.data.difficulties[0]; const value = option ? option.value : ''; this.setData({ difficultyIndex: index, difficulty: value }); this.remember({ difficulty: value }); this.reload(); },
  changeStage(event) { const index = Number(event.detail.value) || 0; const option = this.data.stages[index] || this.data.stages[0]; const value = option ? option.value : ''; this.setData({ stageIndex: index, stage: value }); this.remember({ stage: value }); this.reload(); },
  selectNotebookStatus(event) { const value = event.currentTarget.dataset.value || ''; this.setData({ notebookStatus: value }); this.remember({ notebookStatus: value }); this.reload(); },
  onKeywordInput(event) { this.setData({ keyword: event.detail.value }); },
  searchNotebook() { this.reload(); },
  clearKeyword() { this.setData({ keyword: '' }); this.reload(); },
  retry() { this.reload(); },
  reload(done) { this.setData({ page: 1, items: [], hasMore: false }); this.load(false, done); if (this.data.primaryTab === 'english' && this.data.englishTab === 'notebook') this.loadNotebookSummary(); },
  load(append, done) {
    if (append) this.setData({ loadingMore: true }); else this.setData({ loading: true, error: '' });
    const params = this.queryParams(append ? this.data.page + 1 : 1);
    learningService.list(params).then((result) => {
      const rows = (result.items || []).map((item) => this.decorate(item));
      this.setData({ items: append ? this.data.items.concat(rows) : rows, page: result.page || params.page, hasMore: !!result.hasMore, loading: false, loadingMore: false, error: '' });
      if (done) done();
    }).catch((error) => { this.setData({ loading: false, loadingMore: false, error: error.message || '学习内容加载失败' }); if (done) done(); });
  },
  queryParams(page) {
    const base = { page, pageSize: 20 };
    if (this.data.primaryTab === 'tech') return Object.assign(base, { type: 'tech', topicId: this.data.topicId, difficulty: this.data.difficulty });
    if (this.data.englishTab === 'article') return Object.assign(base, { type: 'english_article', difficulty: this.data.difficulty });
    if (this.data.englishTab === 'notebook') return Object.assign(base, { type: 'word', notebook: true, status: this.data.notebookStatus, keyword: this.data.keyword });
    return Object.assign(base, { type: 'word', stage: this.data.stage, difficulty: this.data.difficulty });
  },
  decorate(item) {
    const seconds = Number(item.estimatedSeconds) || 0;
    return Object.assign({}, item, {
      minuteText: Math.max(1, Math.ceil(seconds / 60)), difficultyLabel: this.optionLabel(item.difficulty, this.data.difficulties),
      stageLabel: this.optionLabel(item.stage, this.data.stages),
      statusLabel: item.understood ? '已学习' : (item.learningStatus === 'learning' ? '学习中' : '未开始'),
      familiarityValue: item.familiarityPercent == null ? 0 : item.familiarityPercent
    });
  },
  loadFilters() {
    return learningService.filters().then((filters) => {
      const difficulties = filters.difficulties || [], stages = filters.stages || [], notebookStatuses = filters.notebookStatuses || [];
      let difficultyIndex = difficulties.findIndex((item) => item.value === this.data.difficulty); if (difficultyIndex < 0) difficultyIndex = 0;
      let stageIndex = stages.findIndex((item) => item.value === this.data.stage); if (stageIndex < 0) stageIndex = 0;
      const difficulty = difficulties[difficultyIndex] ? difficulties[difficultyIndex].value : '';
      const stage = stages[stageIndex] ? stages[stageIndex].value : '';
      const notebookStatus = notebookStatuses.some((item) => item.value === this.data.notebookStatus) ? this.data.notebookStatus : '';
      this.setData({ difficulties, stages, notebookStatuses, difficultyIndex, stageIndex, difficulty, stage, notebookStatus });
      this.remember({ difficulty, stage, notebookStatus });
    }).catch(() => this.setData({ difficulties: [{ value: '', label: '全部' }], stages: [{ value: '', label: '全部' }], notebookStatuses: [{ value: '', label: '全部' }] }));
  },
  optionLabel(value, options) { if (!value) return ''; const option = (options || []).find((item) => item.value === value); return option ? option.label : value; },
  loadTopics() { learningService.topics().then((topics) => this.setData({ topics: [{ id: '', name: '全部' }].concat(topics || []) })).catch(() => this.setData({ topics: [{ id: '', name: '全部' }] })); },
  loadNotebookSummary() { learningService.notebookSummary().then((summary) => this.setData({ notebookSummary: summary || { totalCount: 0, dueCount: 0 } })).catch(() => {}); },
  openItem(event) {
    const id = event.currentTarget.dataset.id, type = event.currentTarget.dataset.type;
    const path = type === 'word' ? '/pages/word/detail/index' : type === 'english_article' ? '/pages/article/detail/index' : '/pages/content/detail/index';
    wx.navigateTo({ url: `${path}?id=${encodeURIComponent(id)}&returnTo=learn` });
  },
  startReview() { if (!this.data.notebookSummary.dueCount) return wx.showToast({ title: '今天暂无待复习词条', icon: 'none' }); wx.navigateTo({ url: '/pages/review/index?scope=word-book' }); },
  remember(partial) { const previous = wx.getStorageSync(STORAGE_KEY) || {}; wx.setStorageSync(STORAGE_KEY, Object.assign(previous, partial)); }
});
