const { reviewService } = require('../../services/index');
const { formatDate } = require('../../utils/date');
Page({
  data: { loading: true, sending: false, date: '', queue: [], current: null, total: 0, completed: 0, progress: 0, error: '' },
  onLoad(options) { this.setData({ date: options.date || formatDate(new Date()) }); this.load(); },
  load() { reviewService.queue(this.data.date).then((queue) => this.apply(queue, queue.length)).catch((error) => this.setData({ loading: false, error: error.message || '复习队列加载失败' })); },
  apply(queue, total) { const completed = Math.max(0, total - queue.length); this.setData({ queue, current: queue[0] || null, total, completed, progress: total ? Math.round(completed * 100 / total) : 100, loading: false, error: '' }); },
  feedback(e) { if (this.data.sending || !this.data.current) return; const feedback = e.currentTarget.dataset.value; const total = this.data.total; this.setData({ sending: true }); reviewService.feedback(this.data.current.scheduleId, { feedback, taskId: this.data.current.taskId }).then((queue) => { this.apply(queue, total); if (!queue.length) wx.showToast({ title: '今日复习完成', icon: 'success' }); }).catch((error) => wx.showToast({ title: error.message || '反馈失败', icon: 'none' })).finally(() => this.setData({ sending: false })); }
});
