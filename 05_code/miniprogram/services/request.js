const config = require('../config/index');

function buildUrl(path) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  return `${config.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`;
}

function request(options = {}) {
  const app = getApp();
  const token = app.globalData.accessToken || wx.getStorageSync('access_token');

  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(options.url || ''),
      method: options.method || 'GET',
      data: options.data,
      timeout: options.timeout || config.requestTimeout,
      header: Object.assign(
        { 'content-type': 'application/json' },
        token ? { Authorization: `Bearer ${token}` } : {},
        options.header || {}
      ),
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data);
          return;
        }

        const error = new Error(
          (response.data && response.data.message) || `请求失败（${response.statusCode}）`
        );
        error.statusCode = response.statusCode;
        error.code = response.data && response.data.code;
        error.data = response.data;
        if (response.statusCode === 401) {
          app.globalData.accessToken = '';
          app.globalData.userInfo = null;
          wx.removeStorageSync('access_token');
          wx.removeStorageSync('user_info');
          wx.removeStorageSync('f01_registration');
        }
        reject(error);
      },
      fail(error) {
        reject(error);
      }
    });
  });
}

module.exports = { request };
