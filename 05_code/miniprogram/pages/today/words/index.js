const { dailyTaskService, wordStudyService } = require('../../../services/index');
const { queueNotice } = require('../../../utils/word-study-entry');
const { formatBusinessDate: formatDate } = require('../../../utils/date');

Page({
  data: { loading: true, error: '', date: '', title: '', words: [], bookName: '', selectedTime: '',
    targetCount: null, selectionNote: '', fixed: false, doneCount: 0, unavailableCount: 0, isToday: false },
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
    const read =
      this.data.date === formatDate(new Date())
        ? dailyTaskService.selectWords(this.data.date)
        : dailyTaskService.day(this.data.date);
    return read
      .then((pack) => {
        const words = (pack.tasks || []).filter(
          (t) => t.taskType === 'word' && t.status !== 'CANCELLED',
        );
        const selected = pack.wordsSelectedAt ? new Date(pack.wordsSelectedAt) : null;
        const local = selected && !Number.isNaN(selected.getTime()) ? new Date(selected.getTime() + 8 * 3600000) : null;
        if (generation === this._generation) this.setData({ words, loading: false,
          bookName: pack.wordsBookName || '学习词书', fixed: !!pack.wordsSelectedAt,
          targetCount: pack.wordsTargetCount == null ? null : pack.wordsTargetCount,
          selectedTime: local ? `${local.getUTCMonth()+1}月${local.getUTCDate()}日 ${String(local.getUTCHours()).padStart(2,'0')}:${String(local.getUTCMinutes()).padStart(2,'0')}` : '',
          selectionNote: pack.wordsSelectionNote || '', isToday: this.data.date === formatDate(new Date()),
          doneCount: words.filter((word) => word.status === 'DONE').length,
          unavailableCount: Number(pack.wordsUnavailableCount || 0),
        });
      })
      .catch((error) => {
        if (generation === this._generation)
          this.setData({ loading: false, error: error.message || '单词列表加载失败' });
      });
  },
  openWord(e) {
    const { id, taskid, version } = e.currentTarget.dataset;
    if (!id) return;
    const word = this.data.words.find((item) => item.contentId === id);
    if (word && word.available === false) return wx.showToast({ title: word.unavailableReason || '词条暂不可用', icon: 'none' });
    if (this.data.date !== formatDate(new Date()) || (word && word.status === 'DONE'))
      return wx.navigateTo({ url: `/pages/word/detail/index?id=${id}&readonly=1` });
    return this.continueStudy(id);
  },
  continueStudy(preferred) {
    if (this.data.date !== formatDate(new Date())) {
      this.setData({ isToday: false });
      return wx.showToast({ title: '这是历史词表，请返回首页学习今天的新词', icon: 'none' });
    }
    if (this._opening) return this._opening;
    this._opening = wordStudyService.queue('daily', typeof preferred === 'string' ? preferred : undefined)
      .then((queue) => {
        if (!queue.current) return wx.showToast({ title: queueNotice(queue), icon: 'none' });
        wx.navigateTo({ url: `/pages/word/detail/index?id=${queue.current.contentId}&daily=1` });
      })
      .catch((error) => wx.showToast({ title: error.message || '学习队列加载失败', icon: 'none' }))
      .finally(() => { this._opening = null; });
    return this._opening;
  },
});
