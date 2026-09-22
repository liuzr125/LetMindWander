const { vocabularyBookService, dailyTaskService } = require('../../../services/index');
const { formatDate } = require('../../../utils/date');

const TYPE_LABELS = {
  k12: '中小学',
  university: '大学英语',
  postgraduate: '考研',
  study_abroad: '出国考试',
  general: '通用词汇'
};

// 选好词书后回到的页面（词书学习进度）
const PROGRESS_ROUTE = 'pages/word/books/progress/index';

Page({
  data: { loading: true, saving: false, error: '', date: '', books: [], selectedIndex: -1, selectedId: '', currentProgress: null },
  onLoad(options) {
    this.setData({ date: options.date || formatDate(new Date()) });
    this.load();
  },
  load() {
    this.setData({ loading: true, error: '' });
    vocabularyBookService.list().then((books) => {
      const items = (books || []).map((book) => Object.assign({}, book, {
        typeLabel: TYPE_LABELS[book.bookType] || '英语词汇',
        countText: book.wordCount ? `${book.wordCount} 词` : '词库待补充',
        initial: (book.bookName || '词').slice(0, 1),
        available: Number(book.wordCount || 0) > 0
      }));
      const selectedIndex = items.findIndex((book) => book.selected && book.available);
      this.setData({
        books: items,
        selectedIndex,
        selectedId: selectedIndex >= 0 ? items[selectedIndex].id : '',
        loading: false
      });
      if (selectedIndex >= 0) return vocabularyBookService.progress('all', 1, 1).then((currentProgress) => this.setData({ currentProgress })).catch(() => {});
    }).catch((error) => this.setData({ loading: false, error: error.message || '词书加载失败' }));
  },
  openProgress() { wx.navigateTo({ url: '/pages/word/books/progress/index' }); },
  choose(event) {
    if (this.data.saving) return;
    // 用下标定位（避免依赖 dataset 的 id 字符串比对），点哪本选哪本
    const index = Number(event.currentTarget.dataset.index);
    const book = this.data.books[index];
    if (!book) return;
    if (!book.available) return wx.showToast({ title: '该词书内容正在准备中', icon: 'none' });
    if (index === this.data.selectedIndex) return;
    this.setData({ selectedIndex: index, selectedId: book.id });
    wx.showToast({ title: `已选择 ${book.bookName}`, icon: 'none', duration: 900 });
  },
  confirm() {
    if (!this.data.selectedId || this.data.saving) return wx.showToast({ title: '请先选择一本词书', icon: 'none' });
    const book = this.data.books.find((item) => item.id === this.data.selectedId);
    if (!book || !book.available) return wx.showToast({ title: '该词书内容正在准备中', icon: 'none' });
    this.setData({ saving: true });
    wx.showLoading({ title: '正在切换词书' });
    vocabularyBookService.select(this.data.selectedId)
      .then(() => dailyTaskService.day(this.data.date))
      .then(() => {
        wx.hideLoading();
        this.setData({ saving: false });
        wx.showToast({ title: `已切换到《${book.bookName}》`, icon: 'none', duration: 1200 });
        // 选好词书后不直接进入学习，回到「词书学习进度」页；若上一页就是它则直接返回
        const stack = getCurrentPages();
        const previous = stack[stack.length - 2];
        if (previous && previous.route === PROGRESS_ROUTE) return wx.navigateBack();
        wx.redirectTo({ url: `/${PROGRESS_ROUTE}` });
      }).catch((error) => {
        wx.hideLoading();
        this.setData({ saving: false });
        wx.showToast({ title: error.message || '词书选择失败', icon: 'none' });
      });
  }
});
