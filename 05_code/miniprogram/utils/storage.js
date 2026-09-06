function setAccessToken(token) {
  const app = getApp();
  app.globalData.accessToken = token || '';

  if (token) {
    wx.setStorageSync('access_token', token);
  } else {
    wx.removeStorageSync('access_token');
  }
}

function setUserInfo(user) {
  const app = getApp();
  app.globalData.userInfo = user || null;
  if (user) wx.setStorageSync('user_info', user);
  else wx.removeStorageSync('user_info');
}

function setRegistrationContext(context) {
  if (context) wx.setStorageSync('f01_registration', context);
  else wx.removeStorageSync('f01_registration');
}

function getRegistrationContext() {
  return wx.getStorageSync('f01_registration') || null;
}

module.exports = { setAccessToken, setUserInfo, setRegistrationContext, getRegistrationContext };
