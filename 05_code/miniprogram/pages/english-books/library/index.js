const books = require('../../../services/english-books');
Page({
  data: {
    items: [],
    keyword: '',
    shelf: false,
    difficultyIndex: 0,
    difficulties: ['全部难度', '入门', '中级', '进阶'],
    loading: true,
    error: '',
    page: 1,
    hasMore: false,
  },
  onLoad() {
    this.load(false);
  },
  onShow() {
    if (this.loaded) this.load(false);
  },
  onPullDownRefresh() {
    this.load(false).finally(() => wx.stopPullDownRefresh());
  },
  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.load(true);
  },
  onKeyword(e) {
    this.setData({ keyword: e.detail.value });
  },
  changeDifficulty(e) {
    this.setData({ difficultyIndex: Number(e.detail.value) || 0 });
    this.load(false);
  },
  search() {
    this.load(false);
  },
  switchShelf(e) {
    this.setData({ shelf: e.currentTarget.dataset.shelf === 'yes' });
    this.load(false);
  },
  async load(append) {
    const sequence = (this.seq = (this.seq || 0) + 1);
    this.setData({ loading: true, error: '' });
    try {
      const result = await books.list({
        keyword: this.data.keyword,
        shelf: this.data.shelf,
        difficulty: ['', 'intro', 'intermediate', 'advanced'][this.data.difficultyIndex],
        page: append ? this.data.page + 1 : 1,
      });
      if (sequence !== this.seq) return;
      const items = result.items.map((b) =>
        Object.assign({}, b, {
          completed: (b.progress.completed || []).length,
          progressText: `${(b.progress.completed || []).length} / ${b.chapterCount} 章`,
        }),
      );
      this.setData({
        items: append ? this.data.items.concat(items) : items,
        page: result.page,
        hasMore: result.hasMore,
      });
      this.loaded = true;
      const firstIndex = append ? this.data.items.length - items.length : 0;
      items.forEach((b, i) => {
        if (b.coverAssetId)
          books
            .media(b.id, b.coverAssetId)
            .then((r) => {
              if (sequence === this.seq)
                this.setData({ [`items[${firstIndex + i}].coverUrl`]: r.url });
            })
            .catch(() => {});
      });
    } catch (e) {
      if (sequence === this.seq) this.setData({ error: e.message || '书库加载失败' });
    } finally {
      if (sequence === this.seq) this.setData({ loading: false });
    }
  },
  open(e) {
    wx.navigateTo({
      url: `/pages/english-books/reader/index?id=${encodeURIComponent(e.currentTarget.dataset.id)}`,
    });
  },
});
