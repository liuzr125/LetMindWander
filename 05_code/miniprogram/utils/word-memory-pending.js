const PREFIX = 'word_memory_pending_v1:';
const MAX_AGE = 7 * 24 * 60 * 60 * 1000;

function createStore({ getOwner, storage, now = () => Date.now() }) {
  const keyFor = (owner, session) => PREFIX + encodeURIComponent(owner) + ':' + encodeURIComponent(session);
  function valid(value, owner, session) {
    const body = value && value.payload;
    return value && value.ownerId === owner && value.sessionId === session
      && typeof value.episodeId === 'string' && value.episodeId.length > 0
      && typeof value.key === 'string' && value.key.length > 0
      && Number.isFinite(value.createdAt) && now() - value.createdAt >= 0 && now() - value.createdAt < MAX_AGE
      && body && typeof body.answer === 'string' && body.answer.length <= 1000
      && Number.isInteger(body.expectedVersion) && body.expectedVersion > 0
      && Number.isFinite(body.durationMs) && body.durationMs >= 0 && body.durationMs <= 3600000;
  }
  return {
    load(session) {
      try {
        const owner = getOwner();
        if (!owner || !session) return null;
        const key = keyFor(owner, session), value = storage.get(key);
        if (valid(value, owner, session)) return JSON.parse(JSON.stringify(value));
        if (value) storage.remove(key);
      } catch (ignored) {}
      return null;
    },
    save(attempt) {
      try {
        const owner = getOwner();
        if (!owner || !attempt) return false;
        const value = { ...attempt, ownerId: owner };
        if (!valid(value, owner, attempt.sessionId)) return false;
        storage.set(keyFor(owner, attempt.sessionId), value);
        return true;
      } catch (ignored) { return false; }
    },
    clear(session, attemptKey) {
      try {
        const owner = getOwner();
        if (!owner) return;
        const key = keyFor(owner, session), value = storage.get(key);
        if (value && value.ownerId === owner && value.key === attemptKey) storage.remove(key);
      } catch (ignored) {}
    },
  };
}

const store = createStore({
  getOwner: () => {
    const app = typeof getApp === 'function' ? getApp() : null;
    const user = app && app.globalData && app.globalData.userInfo || wx.getStorageSync('user_info');
    return user && user.id || '';
  },
  storage: {
    get: key => wx.getStorageSync(key),
    set: (key, value) => wx.setStorageSync(key, value),
    remove: key => wx.removeStorageSync(key),
  },
});
module.exports = { ...store, createStore };
