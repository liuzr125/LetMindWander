const { wordMemoryService } = require('../../../../services/index');

Page({
  data: { loading: true, error: '', result: null },
  onLoad(options) { this.setData({ sessionId: options.id || '' }); this.load(); },
  load() { wordMemoryService.result(this.data.sessionId).then((result) => this.setData({ result, loading: false, error: '' })).catch((error) => this.setData({ loading: false, error: error.message || '结果加载失败' })); },
  returnSource() { const target = this.data.result && this.data.result.returnTo; if (target) wx.redirectTo({ url: target }); else wx.navigateBack({ delta: 2 }); },
  openWeak() { wx.switchTab({ url: '/pages/learn/index' }); }
});
