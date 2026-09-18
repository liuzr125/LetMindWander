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

function uploadFile(filePath, url, formData = {}) {
  return new Promise((resolve, reject) => {
    const token = getApp().globalData.accessToken || wx.getStorageSync('access_token');
    wx.uploadFile({
      url: `${apiBaseUrl}${url}`,
      filePath,
      name: 'file',
      formData,
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
      fail(error) {
        const networkError = new Error(error.errMsg || '文件上传网络连接失败');
        networkError.code = 'NETWORK_ERROR';
        networkError.data = error;
        reject(networkError);
      }
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
    return uploadFile(filePath, '/media/upload');
  }
};

const planService = {
  get() { return request({ url: '/plans' }); },
  settings() { return request({ url: '/plans/settings' }); },
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

const contentService = {
  get(id) { return request({ url: `/learning/contents/${encodeURIComponent(id)}` }); },
  speech(id) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/speech`, method: 'POST', timeout: 65000 }); },
  followRecording(id) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/follow-recording` }); },
  uploadFollowRecording(id, filePath, durationMs) {
    return uploadFile(filePath, `/learning/contents/${encodeURIComponent(id)}/follow-recording`, { durationMs: String(durationMs) });
  },
  deleteFollowRecording(id) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/follow-recording`, method: 'DELETE' }); },
  understood(id, payload) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/understood`, method: 'POST', data: payload }); },
  favorite(id, active) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/favorite`, method: 'POST', data: { active } }); },
  review(id, active) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/review`, method: 'POST', data: { active } }); },
  wordBook(id, active) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/word-book`, method: 'POST', data: { active } }); },
  familiarity(id, familiarityPercent, expectedVersion) { return request({ url: `/learning/contents/${encodeURIComponent(id)}/familiarity`, method: 'PUT', data: { familiarityPercent, expectedVersion } }); }
};

const learningService = {
  list(params = {}) {
    const query = Object.keys(params).filter((key) => params[key] !== '' && params[key] !== null && params[key] !== undefined)
      .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`).join('&');
    return request({ url: `/learning/contents${query ? `?${query}` : ''}` });
  },
  filters() { return request({ url: '/learning/filters' }); },
  topics() { return request({ url: '/learning/topics' }); },
  notebookSummary() { return request({ url: '/learning/notebook/summary' }); }
};

const vocabularyBookService = {
  list() { return request({ url: '/vocabulary-books' }); },
  current() { return request({ url: '/vocabulary-books/current' }); },
  progress(status = 'all', page = 1, pageSize = 20) { return request({ url: `/vocabulary-books/current/progress?status=${encodeURIComponent(status)}&page=${page}&pageSize=${pageSize}` }); },
  select(bookId, dailyNewLimit) {
    const data = { bookId }; if (dailyNewLimit !== undefined && dailyNewLimit !== null) data.dailyNewLimit = dailyNewLimit;
    return request({ url: '/vocabulary-books/current', method: 'PUT', data });
  }
};

const journalService = {
  get(date) { return request({ url: `/journals/${encodeURIComponent(date)}` }); },
  history() { return request({ url: '/journals' }); },
  save(date, payload) { return request({ url: `/journals/${encodeURIComponent(date)}`, method: 'PUT', data: payload }); },
  submit(date, payload) { return request({ url: `/journals/${encodeURIComponent(date)}/submit`, method: 'POST', data: payload }); }
};

const reviewService = {
  queue(date) { return request({ url: `/reviews/queue?date=${encodeURIComponent(date)}` }); },
  feedback(scheduleId, payload) { return request({ url: `/reviews/${encodeURIComponent(scheduleId)}/feedback`, method: 'POST', data: payload }); }
};

const wordMemoryService = {
  config() { return request({ url: '/word-memory/config' }); },
  hints(contentId, senseId) {
    const suffix = senseId ? `&senseId=${encodeURIComponent(senseId)}` : '';
    return request({ url: `/word-memory/hints?contentId=${encodeURIComponent(contentId)}${suffix}` });
  },
  createSession(payload, idempotencyKey) {
    return request({ url: '/word-memory/sessions', method: 'POST', data: payload, header: { 'Idempotency-Key': idempotencyKey } });
  },
  getSession(id) { return request({ url: `/word-memory/sessions/${encodeURIComponent(id)}` }); },
  revealHint(sessionId, episodeId, payload) {
    return request({ url: `/word-memory/sessions/${encodeURIComponent(sessionId)}/episodes/${encodeURIComponent(episodeId)}/hint`, method: 'POST', data: payload });
  },
  attempt(sessionId, episodeId, payload, idempotencyKey) {
    return request({ url: `/word-memory/sessions/${encodeURIComponent(sessionId)}/episodes/${encodeURIComponent(episodeId)}/attempts`, method: 'POST', data: payload, header: { 'Idempotency-Key': idempotencyKey } });
  },
  finish(sessionId, payload) { return request({ url: `/word-memory/sessions/${encodeURIComponent(sessionId)}/finish`, method: 'POST', data: payload }); },
  result(sessionId) { return request({ url: `/word-memory/sessions/${encodeURIComponent(sessionId)}/result` }); }
};

const actionService = {
  get(id) { return request({ url: `/actions/${encodeURIComponent(id)}` }); },
  update(id, payload) { return request({ url: `/actions/${encodeURIComponent(id)}`, method: 'PUT', data: payload }); }
};

const growthService = {
  overview(weekStart) { return request({ url: `/growth${weekStart ? `?weekStart=${encodeURIComponent(weekStart)}` : ''}` }); }
};

const aiService = {
  models() { return request({ url: '/ai/models' }); },
  ask(payload, idempotencyKey) { return request({ url: '/ai/ask', method: 'POST', data: payload, timeout: 65000, header: { 'Idempotency-Key': idempotencyKey } }); },
  history(size = 20) { return request({ url: `/ai/ask/history?size=${size}` }); }
};

const weeklySummaryService = {
  get(weekStart) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}` }); },
  edit(weekStart, payload) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}`, method: 'PUT', data: payload }); },
  confirm(weekStart, payload) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}/confirm`, method: 'POST', data: payload }); },
  recalculate(weekStart) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}/recalculate`, method: 'POST' }); },
  actions(weekStart) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}/actions` }); },
  saveAction(weekStart, payload) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}/actions`, method: 'POST', data: payload }); },
  removeAction(weekStart, actionId, expectedVersion) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}/actions/${encodeURIComponent(actionId)}?expectedVersion=${encodeURIComponent(expectedVersion)}`, method: 'DELETE' }); },
  confirmActions(weekStart, confirmTemporaryDays) { return request({ url: `/weekly-summaries/${encodeURIComponent(weekStart)}/actions/confirm`, method: 'POST', data: { confirmTemporaryDays } }); }
};

const knowledgeService = {
  list(params = {}) {
    const query = Object.keys(params)
      .filter((key) => params[key] !== '' && params[key] !== null && params[key] !== undefined)
      .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
      .join('&');
    return request({ url: `/knowledge${query ? `?${query}` : ''}` });
  },
  tags() { return request({ url: '/knowledge/tags' }); },
  friends() { return request({ url: '/knowledge/friends' }); },
  get(id) { return request({ url: `/knowledge/${encodeURIComponent(id)}` }); },
  create(payload) { return request({ url: '/knowledge', method: 'POST', data: payload }); },
  update(id, payload) { return request({ url: `/knowledge/${encodeURIComponent(id)}`, method: 'PUT', data: payload }); },
  verify(id, payload) { return request({ url: `/knowledge/${encodeURIComponent(id)}/verification`, method: 'PUT', data: payload }); },
  visibility(id, payload) { return request({ url: `/knowledge/${encodeURIComponent(id)}/visibility`, method: 'PUT', data: payload }); },
  review(id, active) { return request({ url: `/knowledge/${encodeURIComponent(id)}/review`, method: 'POST', data: { active } }); },
  remove(id, expectedVersion) { return request({ url: `/knowledge/${encodeURIComponent(id)}?expectedVersion=${encodeURIComponent(expectedVersion)}`, method: 'DELETE' }); }
};

const mineService = {
  overview() { return request({ url: '/mine/overview' }); },
  favorites(params = {}) { const query = Object.keys(params).filter((key) => params[key]).map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`).join('&'); return request({ url: `/mine/favorites${query ? `?${query}` : ''}` }); },
  privacy() { return request({ url: '/mine/privacy' }); },
  requestExport() { return request({ url: '/mine/exports', method: 'POST' }); },
  requestDeletion() { return request({ url: '/mine/deletions', method: 'POST' }); },
  feedback(payload) { return request({ url: '/mine/feedback', method: 'POST', data: payload }); }
};
const friendService = {
  list() { return request({ url: '/friends' }); },
  search(type, query) { return request({ url: `/friends/search?type=${encodeURIComponent(type)}&query=${encodeURIComponent(query)}` }); },
  requests(direction) { return request({ url: `/friends/requests?direction=${encodeURIComponent(direction || 'received')}` }); },
  send(payload) { return request({ url: '/friends/requests', method: 'POST', data: payload }); },
  decide(id, decision, expectedVersion) { return request({ url: `/friends/requests/${encodeURIComponent(id)}/decision`, method: 'POST', data: { decision, expectedVersion } }); },
  shared(id) { return request({ url: `/friends/${encodeURIComponent(id)}/shared` }); },
  remove(id) { return request({ url: `/friends/${encodeURIComponent(id)}`, method: 'DELETE' }); }
};
const resourceScheduleService = {
  list(state) { return request({ url: `/resource-schedules${state && state !== 'all' ? `?state=${encodeURIComponent(state)}` : ''}` }); },
  get(id) { return request({ url: `/resource-schedules/${encodeURIComponent(id)}` }); },
  sources() { return request({ url: '/resource-schedules/sources/available' }); },
  create(payload) { return request({ url: '/resource-schedules', method: 'POST', data: payload }); },
  update(id, payload) { return request({ url: `/resource-schedules/${encodeURIComponent(id)}`, method: 'PUT', data: payload }); },
  state(id, state, expectedVersion) { return request({ url: `/resource-schedules/${encodeURIComponent(id)}/state`, method: 'POST', data: { state, expectedVersion } }); }
};

module.exports = { systemService, authService, profileService, planService, dailyTaskService, contentService, learningService, vocabularyBookService, journalService, reviewService, wordMemoryService, actionService, growthService, aiService, weeklySummaryService, knowledgeService, mineService, friendService, resourceScheduleService };
