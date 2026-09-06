const { dailyTaskService } = require('../../services/index');
const { formatDate } = require('../../utils/date');
Page({
  data: { loading: true, today: '', pack: null, error: '' },
  onShow() { this.load(); },
  load() { const today = formatDate(new Date()); this.setData({ loading: true, today, error: '' }); dailyTaskService.day(today).then(pack => this.setData({ pack, loading: false })).catch(error => this.setData({ error: error.message || '加载今日任务失败', loading: false })); },
  activate() { wx.showLoading({ title: '正在激活' }); dailyTaskService.activate(this.data.today).then(pack => { this.setData({ pack }); wx.showToast({ title: '今日任务已激活', icon: 'success' }); }).catch(error => wx.showToast({ title: error.message || '激活失败', icon: 'none' })).finally(() => wx.hideLoading()); },
  changeTask(event) { const task = event.currentTarget.dataset.task; const eventType = task.status === 'TODO' ? 'start' : task.status === 'DOING' ? 'complete' : task.status === 'DONE' ? 'undo' : 'skip'; dailyTaskService.event(task.id, { eventType, expectedVersion: task.versionNo }).then(() => this.load()).catch(error => { wx.showToast({ title: error.message || '状态更新失败', icon: 'none' }); this.load(); }); }
});
