const { knowledgeService } = require('../../../services/index');

Page({
  data: { id: '', loading: true, saving: false, visibility: 'private', selectedFriendIds: [], friends: [], visibleFriends: [], query: '', versionNo: 0, title: '' },
  onLoad(options) {
    this.setData({ id: options.id || '' });
    const channel = this.getOpenerEventChannel(); this.channel = channel;
    if (channel && channel.on) channel.on('shareDraft', (value) => this.setData({ visibility: value.visibility || 'private', selectedFriendIds: value.selectedFriendIds || [] }));
    const tasks = [knowledgeService.friends()];
    if (this.data.id) tasks.push(knowledgeService.get(this.data.id));
    Promise.all(tasks).then((results) => {
      const friends = (results[0] || []).map((friend) => Object.assign({}, friend, { checked: false, initial: (friend.nickname || '?').slice(0, 1) }));
      const detail = results[1]; const selected = detail ? (detail.selectedFriendIds || []) : this.data.selectedFriendIds;
      friends.forEach((friend) => { friend.checked = selected.indexOf(friend.id) >= 0; });
      this.setData({ friends, visibleFriends: friends, selectedFriendIds: selected, visibility: detail ? detail.visibility : this.data.visibility, versionNo: detail ? detail.versionNo : 0, title: detail ? detail.title : '', loading: false });
    }).catch((error) => { this.setData({ loading: false }); wx.showModal({ title: '加载失败', content: error.message || '请稍后重试', showCancel: false }); });
  },
  selectVisibility(event) {
    const visibility = event.currentTarget.dataset.value;
    this.setData({ visibility, selectedFriendIds: visibility === 'selected' ? this.data.selectedFriendIds : [] });
  },
  onSearch(event) {
    const query = event.detail.value.trim().toLowerCase();
    this.setData({ query, visibleFriends: this.data.friends.filter((friend) => !query || friend.nickname.toLowerCase().indexOf(query) >= 0) });
  },
  onFriendChange(event) {
    const visibleIds = this.data.visibleFriends.map((friend) => friend.id);
    const selected = this.data.selectedFriendIds.filter((id) => visibleIds.indexOf(id) < 0).concat(event.detail.value);
    const friends = this.data.friends.map((friend) => Object.assign({}, friend, { checked: selected.indexOf(friend.id) >= 0 }));
    this.setData({ friends, visibleFriends: friends.filter((friend) => !this.data.query || friend.nickname.toLowerCase().indexOf(this.data.query) >= 0), selectedFriendIds: selected });
  },
  confirm() {
    if (this.data.visibility === 'selected' && !this.data.selectedFriendIds.length) return wx.showToast({ title: '请至少选择一位好友', icon: 'none' });
    const value = { visibility: this.data.visibility, selectedFriendIds: this.data.selectedFriendIds };
    if (!this.data.id) { if (this.channel && this.channel.emit) this.channel.emit('shareSelected', value); wx.navigateBack(); return; }
    if (this.data.saving) return; this.setData({ saving: true });
    knowledgeService.visibility(this.data.id, Object.assign({ expectedVersion: this.data.versionNo }, value))
      .then(() => { wx.showToast({ title: '可见范围已更新', icon: 'success' }); wx.navigateBack(); })
      .catch((error) => wx.showToast({ title: error.message || '保存失败', icon: 'none' })).finally(() => this.setData({ saving: false }));
  }
});
