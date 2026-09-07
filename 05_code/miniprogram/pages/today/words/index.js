const { contentService, dailyTaskService } = require('../../../services/index');
const { formatDate } = require('../../../utils/date');

Page({
  data: { loading: true, error: '', date: '', title: '', words: [] },
  onLoad(options) {
    const date = options.date || formatDate(new Date());
    const parts = date.split('-');
    const title = `${Number(parts[1])}月${Number(parts[2])}日`;
    this.setData({ date, title });
    this.load();
  },
  load() {
    this.setData({ loading: true, error: '' });
    dailyTaskService.day(this.data.date).then((pack) => {
      const words = (pack.tasks || []).filter((t) => t.taskType === 'word' && t.status !== 'CANCELLED');
      return this.attachMeanings(words);
    }).then((words) => this.setData({ words, loading: false }))
      .catch((error) => this.setData({ loading: false, error: error.message || '单词列表加载失败' }));
  },
  attachMeanings(words) {
    return Promise.all(words.map((word) => contentService.get(word.contentId)
      .then((detail) => Object.assign({}, word, { meaning: detail.meaning || '' }))
      .catch(() => Object.assign({}, word, { meaning: '' }))));
  },
  openWord(e) {
    const { id, taskid, version } = e.currentTarget.dataset;
    if (!id) return;
    wx.navigateTo({ url: `/pages/word/detail/index?id=${id}&taskId=${taskid || ''}&version=${version || 1}` });
  }
});
