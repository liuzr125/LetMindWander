const { journalService } = require('../../../services/index');
Page({
  data: { loading: true, items: [], error: '' },
  onShow() { journalService.history().then((items) => this.setData({ items, loading: false, error: '' })).catch((error) => this.setData({ loading: false, error: error.message || '历史加载失败' })); },
  open(e) { wx.navigateTo({ url: `/pages/journal/edit/index?date=${e.currentTarget.dataset.date}` }); }
});
