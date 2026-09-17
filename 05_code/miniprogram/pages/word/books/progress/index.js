const { vocabularyBookService } = require('../../../../services/index');

const TABS = [
  { value: 'all', label: '全部' },
  { value: 'learned', label: '已学' },
  { value: 'remaining', label: '未学' }
];

Page({
  data: { loading: true, loadingMore: false, error: '', needsBook: false, progress: null, items: [], status: 'all', tabs: TABS, page: 1, hasMore: false },
  onLoad() { this.initialized = true; this.load(true); },
  onShow() { if (this.initialized && this.shownOnce) this.load(true); this.shownOnce = true; },
  onPullDownRefresh() { this.load(true, () => wx.stopPullDownRefresh()); },
  onReachBottom() { if (this.data.hasMore && !this.data.loadingMore) this.load(false); },
  selectTab(event) { const status = event.currentTarget.dataset.value; if (status === this.data.status) return; this.setData({ status }); this.load(true); },
  load(reset, done) {
    const shouldReset = reset === true;
    const page = shouldReset ? 1 : this.data.page + 1;
    this.setData(shouldReset ? { loading: true, error: '', needsBook: false, page: 1, items: [] } : { loadingMore: true });
    vocabularyBookService.progress(this.data.status, page, 20).then((progress) => {
      const items = shouldReset ? (progress.items || []) : this.data.items.concat(progress.items || []);
      this.setData({ progress, items, page, hasMore: !!progress.hasMore, loading: false, loadingMore: false, error: '', needsBook: false });
    }).catch((error) => this.setData({ loading: false, loadingMore: false, needsBook: error.code === 'VOCABULARY_BOOK_REQUIRED', error: error.message || '进度加载失败' })).finally(() => { if (done) done(); });
  },
  retry() { this.load(true); },
  openWord(event) { wx.navigateTo({ url: `/pages/word/detail/index?id=${encodeURIComponent(event.currentTarget.dataset.id)}` }); },
  openPlan() { wx.navigateTo({ url: '/pages/plan/index' }); },
  chooseBook() { wx.navigateTo({ url: '/pages/word/books/index' }); }
});
