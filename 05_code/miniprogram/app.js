App({
  globalData: {
    accessToken: '',
    userInfo: null,
    privacyVersion: 'PRIVACY_V1'
  },

  onLaunch() {
    const accessToken = wx.getStorageSync('access_token');
    this.globalData.accessToken = accessToken || '';
    this.globalData.userInfo = wx.getStorageSync('user_info') || null;
  }
});
