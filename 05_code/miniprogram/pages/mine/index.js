const { profileService } = require('../../services/index');
const { setUserInfo } = require('../../utils/storage');

Page({
  data: {
    profile: {},
    defaultAvatar: '/assets/logo.png',
    aiUsed: 2,
    aiLimit: 10,
    sessionExpired: false
  },

  onShow() {
    this.loadProfile();
  },

  loadProfile() {
    const token = getApp().globalData.accessToken || wx.getStorageSync('access_token');
    if (!token) {
      const cached = wx.getStorageSync('user_info');
      if (cached) this.setData({ profile: cached });
      return;
    }
    profileService.get().then((profile) => {
      this.setData({ profile });
      this.setData({ sessionExpired: false });
      setUserInfo(profile);
    }).catch((error) => {
      if (error.statusCode === 401) {
        this.setData({ profile: {}, sessionExpired: true });
        return;
      }
      const cached = wx.getStorageSync('user_info');
      if (cached) this.setData({ profile: cached });
    });
  },

  goProfile() {
    const token = getApp().globalData.accessToken || wx.getStorageSync('access_token');
    wx.navigateTo({ url: token ? '/pages/profile/edit/index' : '/pages/auth/invite/index' });
  },
  goPlan() { wx.navigateTo({ url: '/pages/plan/index' }); },
  goHistory() { wx.navigateTo({ url: '/pages/plan/history/index' }); },
  goFavorites() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goFriends() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goWordbook() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goPrivacy() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goFeedback() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goUsage() { wx.showToast({ title: '功能开发中', icon: 'none' }); }
});
