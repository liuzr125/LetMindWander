const { knowledgeService } = require('../../services/index');

const typeNames = { note: '笔记', problem: '问题卡', word: '词汇', content_ref: '引用' };
const statusNames = { unlearned: '未学习', learning: '学习中', understood: '已理解', mastered: '已掌握' };
const verificationNames = { unverified: '未验证', verified: '已验证', partial: '部分验证', invalid: '已失效' };

function displayTime(value) {
  if (!value) return '';
  const date = new Date(value);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) return `今天 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return '昨天';
  return `${date.getMonth() + 1}-${String(date.getDate()).padStart(2, '0')}`;
}

function decorate(item) {
  const status = item.itemType === 'problem' ? verificationNames[item.verificationStatus] : statusNames[item.learningStatus];
  return Object.assign({}, item, {
    typeName: typeNames[item.itemType] || '知识',
    statusName: item.state === 'draft' ? '草稿' : (status || ''),
    statusTone: item.state === 'draft' || item.verificationStatus === 'unverified' ? 'warning' : (item.learningStatus === 'understood' || item.learningStatus === 'mastered' || item.verificationStatus === 'verified' ? 'success' : 'primary'),
    visibilityName: item.visibility === 'private' ? '只对你可见' : (item.visibility === 'friends' ? '所有好友可见' : '指定好友可见'),
    updatedLabel: displayTime(item.updatedAt),
    icon: item.itemType === 'problem' ? '?' : (item.itemType === 'word' ? 'Aa' : '≡')
  });
}

Page({
  data: {
    loading: true, error: '', items: [], query: '', type: 'all',
    tabs: [{ key: 'all', label: '全部' }, { key: 'note', label: '笔记' }, { key: 'problem', label: '问题卡' }, { key: 'word', label: '词汇' }],
    showFilters: false, learningStatus: '', verificationStatus: '',
    learningOptions: [{ key: '', label: '全部学习状态' }, { key: 'learning', label: '学习中' }, { key: 'understood', label: '已理解' }, { key: 'mastered', label: '已掌握' }],
    verificationOptions: [{ key: '', label: '全部验证状态' }, { key: 'unverified', label: '未验证' }, { key: 'verified', label: '已验证' }, { key: 'partial', label: '部分验证' }]
  },
  onShow() { this.load(); },
  onPullDownRefresh() { this.load(true); },
  onUnload() { if (this.searchTimer) clearTimeout(this.searchTimer); },
  load(fromRefresh) {
    const seq = (this.requestSeq || 0) + 1; this.requestSeq = seq;
    this.setData({ loading: !fromRefresh, error: '' });
    knowledgeService.list({ type: this.data.type, query: this.data.query.trim(), learningStatus: this.data.learningStatus, verificationStatus: this.data.verificationStatus, limit: 50 })
      .then((items) => { if (seq === this.requestSeq) this.setData({ items: (items || []).map(decorate), loading: false }); })
      .catch((error) => { if (seq === this.requestSeq) this.setData({ error: error.message || '知识加载失败', loading: false }); })
      .finally(() => { if (fromRefresh) wx.stopPullDownRefresh(); });
  },
  onQueryInput(event) {
    this.setData({ query: event.detail.value });
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 350);
  },
  clearQuery() { this.setData({ query: '' }); this.load(); },
  selectType(event) { this.setData({ type: event.currentTarget.dataset.key }); this.load(); },
  toggleFilters() { this.setData({ showFilters: !this.data.showFilters }); },
  selectLearning(event) { this.setData({ learningStatus: event.currentTarget.dataset.key }); this.load(); },
  selectVerification(event) { this.setData({ verificationStatus: event.currentTarget.dataset.key }); this.load(); },
  resetFilters() { this.setData({ learningStatus: '', verificationStatus: '', showFilters: false }); this.load(); },
  createKnowledge() { wx.navigateTo({ url: '/pages/knowledge/editor/index' }); },
  openItem(event) {
    const item = this.data.items.find((row) => row.id === event.currentTarget.dataset.id);
    if (!item) return;
    if (item.itemType === 'content_ref' && item.sourceContentId) {
      wx.navigateTo({ url: `/pages/content/detail/index?id=${encodeURIComponent(item.sourceContentId)}&from=knowledge` });
      return;
    }
    wx.navigateTo({ url: `/pages/knowledge/detail/index?id=${encodeURIComponent(item.id)}` });
  },
  openMenu(event) {
    const item = this.data.items.find((row) => row.id === event.currentTarget.dataset.id);
    if (!item) return;
    const labels = ['查看详情'];
    if (item.itemType === 'note' || item.itemType === 'problem' || item.itemType === 'ai_note') labels.push('编辑');
    if (item.state === 'active') labels.push(item.inReview ? '暂停复习' : '加入复习');
    labels.push('删除');
    wx.showActionSheet({ itemList: labels, success: ({ tapIndex }) => {
      const action = labels[tapIndex];
      if (action === '查看详情') this.openItem({ currentTarget: { dataset: { id: item.id } } });
      else if (action === '编辑') wx.navigateTo({ url: `/pages/knowledge/editor/index?id=${encodeURIComponent(item.id)}` });
      else if (action === '加入复习' || action === '暂停复习') this.changeReview(item, !item.inReview);
      else if (action === '删除') wx.navigateTo({ url: `/pages/knowledge/delete/index?id=${encodeURIComponent(item.id)}` });
    } });
  },
  changeReview(item, active) {
    knowledgeService.review(item.id, active).then(() => { wx.showToast({ title: active ? '已加入复习' : '已暂停复习', icon: 'success' }); this.load(); })
      .catch((error) => wx.showToast({ title: error.message || '操作失败', icon: 'none' }));
  }
});
