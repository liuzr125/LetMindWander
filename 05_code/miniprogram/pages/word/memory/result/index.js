const { wordMemoryService } = require('../../../../services/index');

Page({
  data: { loading: true, error: '', result: null },
  onLoad(options) { this.setData({ sessionId: options.id || '' }); this.load(); },
  load() { wordMemoryService.result(this.data.sessionId).then((result) => this.setData({ result, loading: false, error: '' })).catch((error) => this.setData({ loading: false, error: error.message || '结果加载失败' })); },
  returnSource() {
    const target = this.data.result && this.data.result.returnTo;
    if (!target) return wx.navigateBack({ delta: 2 });
    const path = target.split('?')[0];
    const tabPages = ['/pages/today/index', '/pages/learn/index', '/pages/ai/ask/index', '/pages/knowledge/index', '/pages/mine/index'];
    if (tabPages.includes(path)) return wx.switchTab({ url: path });
    wx.redirectTo({ url: target });
  },
  openWeak() { wx.switchTab({ url: '/pages/learn/index' }); }
});
