const { request } = require('./request');
const { apiBaseUrl } = require('../config/index');

const systemService = {
  health() {
    return request({ url: '/health' });
  }
};

const authService = {
  wechatLogin(payload) {
    return request({ url: '/auth/wechat', method: 'POST', data: payload });
  },
  register(payload) {
    return request({ url: '/auth/register', method: 'POST', data: payload });
  },
  sendSmsCode(payload) {
    return request({ url: '/auth/sms-code', method: 'POST', data: payload });
  },
  authorizePhone(payload) {
    return request({ url: '/auth/phone', method: 'POST', data: payload });
  },
  me() {
    return request({ url: '/user' });
  },
  logout() {
    return request({ url: '/auth/logout', method: 'POST' });
  },
  consent(payload) {
    return request({ url: '/consents', method: 'POST', data: payload });
  }
};

function uploadAvatarFile(filePath) {
  return new Promise((resolve, reject) => {
    const token = getApp().globalData.accessToken || wx.getStorageSync('access_token');
    wx.uploadFile({
      url: `${apiBaseUrl}/media/upload`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success(response) {
        let data = response.data;
        try { data = JSON.parse(data); } catch (error) {}
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(data);
          return;
        }
        const err = new Error((data && data.message) || '上传失败');
        err.code = data && data.code;
        reject(err);
      },
      fail(error) { reject(error); }
    });
  });
}

const profileService = {
  rules() { return request({ url: '/profile/rules' }); },
  visible(userId) { return request({ url: `/users/${encodeURIComponent(userId)}/profile` }); },
  get() {
    return request({ url: '/me' });
  },
  update(payload) {
    return request({ url: '/me', method: 'PUT', data: payload });
  },
  uploadAvatar(filePath) {
    return uploadAvatarFile(filePath);
  }
};

const planService = {
  get() { return request({ url: '/plans' }); },
  history() { return request({ url: '/plans/history' }); },
  initializeDefault() { return request({ url: '/plans/default', method: 'POST' }); },
  update(payload) { return request({ url: '/plans', method: 'PUT', data: payload }); },
  topics() { return request({ url: '/plans/topics' }); },
  createTopic(name) { return request({ url: '/plans/topics', method: 'POST', data: { name } }); }
};

const dailyTaskService = {
  day(date) { return request({ url: `/days/${encodeURIComponent(date)}` }); },
  activate(date) { return request({ url: `/days/${encodeURIComponent(date)}/activate`, method: 'POST' }); },
  event(taskId, payload) { return request({ url: `/tasks/${encodeURIComponent(taskId)}/events`, method: 'POST', data: payload }); }
};

module.exports = { systemService, authService, profileService, planService, dailyTaskService };
