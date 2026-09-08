const { knowledgeService } = require('../../../services/index');

const verificationLabels = { unverified: '未验证', verified: '已验证', partial: '部分验证', invalid: '已失效' };
const learningLabels = { unlearned: '未学习', learning: '学习中', understood: '已理解', mastered: '已掌握' };

Page({
  data: { loading: true, error: '', detail: null, acting: false },
  onLoad(options) { this.setData({ id: options.id || '' }); },
  onShow() { if (this.data.id) this.load(); },
  load() {
    this.setData({ loading: true, error: '' });
    knowledgeService.get(this.data.id).then((detail) => {
      const problem = detail.problem || {};
      this.setData({
        detail: Object.assign({}, detail, {
          problem,
          typeName: detail.itemType === 'problem' ? '问题卡' : '笔记',
          statusName: detail.itemType === 'problem' ? verificationLabels[detail.verificationStatus] : learningLabels[detail.learningStatus],
          visibilityName: detail.visibility === 'private' ? '仅自己可见' : (detail.visibility === 'friends' ? '所有好友可见' : `指定好友可见 · ${detail.selectedFriendIds.length} 人`)
        }),
        loading: false
      });
      wx.setNavigationBarTitle({ title: detail.itemType === 'problem' ? '问题卡' : '笔记详情' });
    }).catch((error) => this.setData({ error: error.message || '知识加载失败', loading: false }));
  },
  edit() { wx.navigateTo({ url: `/pages/knowledge/editor/index?id=${encodeURIComponent(this.data.id)}` }); },
  makeNote() { wx.navigateTo({ url: `/pages/knowledge/editor/index?parentId=${encodeURIComponent(this.data.id)}` }); },
  openSource() {
    const id = this.data.detail && this.data.detail.sourceContentId;
    if (id) wx.navigateTo({ url: `/pages/content/detail/index?id=${encodeURIComponent(id)}&from=knowledge` });
  },
  toggleReview() {
    if (this.data.acting) return;
    const active = !this.data.detail.inReview; this.setData({ acting: true });
    knowledgeService.review(this.data.id, active).then((detail) => { this.setData({ detail: Object.assign({}, this.data.detail, detail) }); wx.showToast({ title: active ? '已加入复习' : '已暂停复习', icon: 'success' }); })
      .catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' })).finally(() => this.setData({ acting: false }));
  },
  verify() {
    const labels = ['已验证', '部分验证', '未验证', '已失效'];
    const values = ['verified', 'partial', 'unverified', 'invalid'];
    wx.showActionSheet({ itemList: labels, success: ({ tapIndex }) => {
      const status = values[tapIndex];
      if (status === 'verified' || status === 'partial') {
        wx.showModal({ title: labels[tapIndex], content: '', editable: true, placeholderText: '填写验证说明（必填）', success: (result) => {
          if (result.confirm) this.submitVerification(status, result.content || '');
        } });
      } else this.submitVerification(status, '');
    } });
  },
  submitVerification(status, note) {
    if (this.data.acting) return; this.setData({ acting: true });
    knowledgeService.verify(this.data.id, { status, note, expectedVersion: this.data.detail.versionNo })
      .then(() => { wx.showToast({ title: '验证状态已更新', icon: 'success' }); this.load(); })
      .catch((error) => wx.showToast({ title: error.message || '更新失败', icon: 'none' })).finally(() => this.setData({ acting: false }));
  },
  more() {
    wx.showActionSheet({ itemList: ['编辑可见范围', '删除知识'], success: ({ tapIndex }) => {
      if (tapIndex === 0) this.editVisibility();
      else wx.navigateTo({ url: `/pages/knowledge/delete/index?id=${encodeURIComponent(this.data.id)}` });
    } });
  },
  editVisibility() { wx.navigateTo({ url: `/pages/knowledge/share/index?id=${encodeURIComponent(this.data.id)}` }); }
});
