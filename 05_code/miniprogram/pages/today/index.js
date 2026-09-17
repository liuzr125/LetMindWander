const { dailyTaskService, vocabularyBookService, growthService } = require('../../services/index');
const { formatDate, formatChineseDate } = require('../../utils/date');

const GROUPS = {
  tech: { title: '技术学习', icon: '⌁', color: 'green' },
  word: { title: '英语新词', icon: 'Aa', color: 'blue' },
  review: { title: '到期复习', icon: '▤', color: 'orange' },
  journal: { title: '今日复盘', icon: '✎', color: 'purple' },
  action: { title: '今日行动', icon: '✓', color: 'cyan' }
};

const GAP_LABELS = {
  tech: '技术学习',
  word: '英语新词',
  review: '到期复习',
  journal: '今日复盘',
  action: '今日行动'
};

Page({
  data: { loading: true, today: '', dateText: '', pack: null, packActive: false, hasGaps: false, groups: [], remainingMinutes: 0, error: '', growth: null, growthError: '' },
  onShow() { this.load(); this.loadGrowth(); },
  load() {
    const now = new Date(); const today = formatDate(now);
    this.setData({ loading: true, today, dateText: formatChineseDate(now), error: '' });
    dailyTaskService.day(today).then((pack) => {
      const tasks = (pack.tasks || []).filter((task) => task.status !== 'CANCELLED');
      const groups = Object.keys(GROUPS).map((type) => {
        const items = tasks.filter((task) => task.taskType === type);
        if (!items.length) return null;
        const done = items.filter((task) => task.status === 'DONE').length;
        return Object.assign({ type, items, done, total: items.length, statusText: done === items.length ? '已完成' : `已完成 ${done}/${items.length}` }, GROUPS[type]);
      }).filter(Boolean);
      const remainingMinutes = Math.ceil(tasks.filter((task) => task.status !== 'DONE' && task.status !== 'SKIPPED').reduce((sum, task) => sum + (task.estimatedSeconds || 0), 0) / 60);
      const gaps = (pack.gaps || []).map((gap) => Object.assign({}, gap, { label: GAP_LABELS[gap.taskType] || '计划任务' }));
      const wordGap = gaps.find((gap) => gap.taskType === 'word' && gap.reason === '未选择词书');
      if (wordGap && !groups.some((group) => group.type === 'word')) {
        groups.splice(Math.min(1, groups.length), 0, Object.assign({ type: 'word', items: [], done: 0, total: wordGap.gapCount, statusText: '请先选择学习词书', requiresBook: true }, GROUPS.word));
      }
      this.setData({ pack: Object.assign({}, pack, { gaps }), packActive: !!pack.active, hasGaps: !!gaps.length, groups, remainingMinutes, loading: false });
    }).catch(error => this.setData({ error: error.message || '加载今日任务失败', loading: false }));
  },
  loadGrowth() {
    growthService.overview().then((growth) => this.setData({ growth, growthError: '' }))
      .catch((error) => this.setData({ growth: null, growthError: error.message || '成长数据暂时不可用' }));
  },
  activate() { wx.showLoading({ title: '正在激活' }); dailyTaskService.activate(this.data.today).then(() => { wx.showToast({ title: '今日任务已激活', icon: 'success' }); return this.load(); }).catch(error => wx.showToast({ title: error.message || '激活失败', icon: 'none' })).finally(() => wx.hideLoading()); },
  openGroup(event) {
    const group = this.data.groups.find((item) => item.type === event.currentTarget.dataset.type);
    if (!group) return;
    if (group.type === 'word') return this.openWordGroup(group);
    const task = group.items.find((item) => item.status !== 'DONE' && item.status !== 'SKIPPED') || group.items[0];
    if (task.taskType === 'tech') return wx.navigateTo({ url: `/pages/content/detail/index?id=${task.contentId}&taskId=${task.id}&version=${task.versionNo}` });
    if (task.taskType === 'review') return wx.navigateTo({ url: `/pages/review/index?date=${this.data.today}` });
    if (task.taskType === 'journal') return wx.navigateTo({ url: `/pages/journal/edit/index?date=${this.data.today}` });
    if (task.taskType === 'action') return wx.navigateTo({ url: `/pages/action/detail/index?id=${task.actionId}&taskId=${task.id}&taskVersion=${task.versionNo}&taskStatus=${task.status}` });
  },
  openWordGroup(group) {
    if (group.requiresBook || !group.items.length) return this.openBookSelector();
    wx.showLoading({ title: '正在准备' });
    vocabularyBookService.current().then((book) => {
      wx.hideLoading();
      if (!book || !book.id) return this.openBookSelector();
      if (group.done === group.total) return wx.navigateTo({ url: `/pages/today/words/index?date=${this.data.today}` });
      const task = group.items.find((item) => item.status !== 'DONE' && item.status !== 'SKIPPED') || group.items[0];
      wx.navigateTo({ url: `/pages/word/detail/index?id=${task.contentId}&taskId=${task.id}&version=${task.versionNo}` });
    }).catch((error) => { wx.hideLoading(); wx.showToast({ title: error.message || '词书状态加载失败', icon: 'none' }); });
  },
  openBookSelector() { wx.navigateTo({ url: `/pages/word/books/index?date=${this.data.today}` }); },
  adjustToday() { wx.navigateTo({ url: '/pages/plan/index?mode=today' }); },
  openLearn() { wx.switchTab({ url: '/pages/learn/index' }); },
  openJournalHistory() { wx.navigateTo({ url: '/pages/journal/history/index' }); },
  openGrowth() { wx.navigateTo({ url: '/pages/growth/index' }); }
});
