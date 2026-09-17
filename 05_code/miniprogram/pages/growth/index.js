const { growthService } = require('../../services/index');
const { formatDate } = require('../../utils/date');

function monday(date) {
  const value = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = value.getDay() || 7;
  value.setDate(value.getDate() - day + 1);
  return value;
}

Page({
  data: { loading: true, error: '', weekStart: '', overview: null },
  onLoad() { this.setData({ weekStart: formatDate(monday(new Date())) }); },
  onShow() { if (this.data.weekStart) this.load(); },
  load() {
    this.setData({ loading: true, error: '' });
    growthService.overview(this.data.weekStart).then((overview) => this.setData({ overview, loading: false }))
      .catch((error) => this.setData({ loading: false, error: error.message || '成长数据加载失败' }));
  },
  shiftWeek(event) {
    const delta = Number(event.currentTarget.dataset.delta) || 0;
    const value = new Date(`${this.data.weekStart}T00:00:00`);
    value.setDate(value.getDate() + delta * 7);
    this.setData({ weekStart: formatDate(value) });
    this.load();
  },
  openReview() { wx.navigateTo({ url: `/pages/review/index?date=${formatDate(new Date())}&from=growth` }); },
  openSummary() {
    const summary = this.data.overview && this.data.overview.previousSummary;
    if (!summary || !summary.weekStart) return;
    wx.navigateTo({ url: `/pages/summary/detail/index?weekStart=${summary.weekStart}` });
  }
});
