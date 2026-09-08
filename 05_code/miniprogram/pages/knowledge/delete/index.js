const { knowledgeService } = require('../../../services/index');

Page({
  data: { id: '', detail: null, loading: true, deleting: false },
  onLoad(options) {
    const id = options.id || ''; this.setData({ id });
    knowledgeService.get(id).then((detail) => this.setData({ detail, loading: false }))
      .catch((error) => { this.setData({ loading: false }); wx.showModal({ title: '加载失败', content: error.message || '请稍后重试', showCancel: false }); });
  },
  cancel() { wx.navigateBack(); },
  remove() {
    if (this.data.deleting || !this.data.detail) return;
    this.setData({ deleting: true });
    knowledgeService.remove(this.data.id, this.data.detail.versionNo).then(() => {
      wx.showToast({ title: '知识已删除', icon: 'success' });
      wx.switchTab({ url: '/pages/knowledge/index' });
    }).catch((error) => wx.showToast({ title: error.message || '删除失败', icon: 'none' })).finally(() => this.setData({ deleting: false }));
  }
});
