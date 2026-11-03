const test = require('node:test');
const assert = require('node:assert/strict');
const helper = import('../src/services/sessionRestore.js');
test('backend restart does not clear a valid browser token on 500', async () => {
  let cleared = false;
  const { restoreAdminProfile } = await helper;
  const result = await restoreAdminProfile({
    getToken: () => 'existing',
    clearToken: () => {
      cleared = true;
    },
    request: async () => {
      throw Object.assign(new Error('restart'), { status: 500 });
    },
  });
  assert.equal(result.state, 'unavailable');
  assert.equal(cleared, false);
});
test('network failures retain the session for retry', async () => {
  let cleared = false;
  const { restoreAdminProfile } = await helper;
  const result = await restoreAdminProfile({
    getToken: () => 'existing',
    clearToken: () => {
      cleared = true;
    },
    request: async () => {
      throw new TypeError('offline');
    },
  });
  assert.equal(result.state, 'unavailable');
  assert.equal(cleared, false);
});
test('an actual unauthorized response clears an expired session', async () => {
  let cleared = false;
  const { restoreAdminProfile } = await helper;
  const result = await restoreAdminProfile({
    getToken: () => 'existing',
    clearToken: () => {
      cleared = true;
    },
    request: async () => {
      throw Object.assign(new Error('expired'), { status: 401 });
    },
  });
  assert.equal(result.state, 'expired');
  assert.equal(cleared, true);
});
test('healthy restore returns the authenticated profile without changing the token', async () => {
  const { restoreAdminProfile } = await helper;
  const profile = { username: 'superadmin' };
  const result = await restoreAdminProfile({
    getToken: () => 'existing',
    clearToken: () => {
      throw Error('must not clear');
    },
    request: async () => profile,
  });
  assert.equal(result.state, 'ready');
  assert.equal(result.profile, profile);
});
