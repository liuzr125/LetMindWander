const { contentService, dailyTaskService } = require('../../../services/index');
const { formatBusinessDate: formatDate } = require('../../../utils/date');

Page({
  data: { loading: true, error: '', date: '', title: '', words: [] },
  onLoad(options) {
    const date = options.date || formatDate(new Date());
    const parts = date.split('-');
    const title = `${Number(parts[1])}月${Number(parts[2])}日`;
    this.setData({ date, title });
    this.load();
  },
  // 首次 onLoad 已读取；从单词反馈页返回时重新读取真实 DONE 状态。
  onShow() {
    if (this._shown) this.load();
    this._shown = true;
  },
  load() {
    // 较早请求晚返回时不能覆盖反馈后的新任务状态。
    const generation = (this._generation || 0) + 1;
    this._generation = generation;
    this.setData({ loading: true, error: '' });
    return dailyTaskService
      .day(this.data.date)
      .then((pack) => {
        const words = (pack.tasks || []).filter(
          (t) => t.taskType === 'word' && t.status !== 'CANCELLED',
        );
        return this.attachMeanings(words);
      })
      .then((words) => {
        if (generation === this._generation) this.setData({ words, loading: false });
      })
      .catch((error) => {
        if (generation === this._generation)
          this.setData({ loading: false, error: error.message || '单词列表加载失败' });
      });
  },
  attachMeanings(words) {
    return Promise.all(
      words.map((word) =>
        contentService
          .get(word.contentId)
          .then((detail) => Object.assign({}, word, { meaning: detail.meaning || '' }))
          .catch(() => Object.assign({}, word, { meaning: '' })),
      ),
    );
  },
  openWord(e) {
    const { id, taskid, version } = e.currentTarget.dataset;
    if (!id) return;
    wx.navigateTo({
      url: `/pages/word/detail/index?id=${id}&taskId=${taskid || ''}&version=${version || 1}`,
    });
  },
});
