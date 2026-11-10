const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup() {
  let page;
  const events = {};
  const state = {
    granted: false,
    mic: undefined,
    starts: 0,
    grants: 0,
    uploads: [],
    modals: [],
    toasts: [],
  };
  const recorder = {
    onStart(fn) {
      events.start = fn;
    },
    onStop(fn) {
      events.stop = fn;
    },
    onError(fn) {
      events.error = fn;
    },
    offStart() {
      delete events.start;
    },
    offStop() {
      delete events.stop;
    },
    offError() {
      delete events.error;
    },
    start() {
      state.starts += 1;
    },
    stop() {},
  };
  const services = {
    mineService: { privacy: async () => ({ followRecordingConsent: state.granted }) },
    authService: {
      consent: async (payload) => {
        state.grants += 1;
        assert.equal(payload.purpose, 'follow_recording_upload');
        state.granted = true;
      },
    },
    contentService: {
      followRecording: async () => ({ exists: false }),
      uploadFollowRecording: async (...args) => {
        state.uploads.push(args);
        return { previewUrl: 'private-preview', durationMs: args[2] };
      },
    },
  };
  const wx = {
    createInnerAudioContext: () => ({ onEnded() {}, onStop() {}, stop() {}, destroy() {} }),
    getRecorderManager: () => recorder,
    // 模拟微信的参数约束，旧文案“同意并录音”会在此失败，根本不会提交授权。
    showModal(options) {
      state.modals.push(options);
      if (Array.from(options.confirmText || '确定').length > 4) {
        options.fail({
          errMsg: 'showModal:fail parameter error: confirmText length should not be larger than 4',
        });
      } else options.success({ confirm: true });
    },
    getSetting: (options) => options.success({ authSetting: { 'scope.record': state.mic } }),
    authorize(options) {
      state.mic = true;
      options.success();
    },
    showToast: (options) => state.toasts.push(options),
    getStorageSync() {
      throw new Error('不得读取旧的全局授权缓存');
    },
    setStorageSync() {
      throw new Error('storage quota exceeded');
    },
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, '../pages/article/detail/index.js'), 'utf8'),
    {
      Page: (value) => {
        page = value;
      },
      require: () => services,
      wx,
    },
  );
  page.data = JSON.parse(JSON.stringify(page.data));
  page.setData = (values) => Object.assign(page.data, values);
  page.load = () => {};
  page.loadFollowRecording = () => {};
  page.onLoad({ id: 'article-a' });
  return { page, wx, services, state, events };
}

test('first consent uses valid native button text and recording waits for native onStart', async () => {
  const { page, state, events } = setup();
  await page.startFollow();
  assert.equal(state.grants, 1);
  assert.equal(state.starts, 1);
  assert.equal(page.data.recording, false);
  assert.equal(page.data.recordStarting, true);
  events.start();
  assert.equal(page.data.recording, true);
  assert.equal(page.data.recordStarting, false);
  assert.equal(page.data.recordError, '');
});

test('rapid taps issue one authorization and one recorder start', async () => {
  const { page, state } = setup();
  await Promise.all([page.startFollow(), page.startFollow(), page.startFollow()]);
  assert.equal(state.modals.length, 1);
  assert.equal(state.grants, 1);
  assert.equal(state.starts, 1);
});

test('cancel does not save consent or ask for microphone permission', async () => {
  const { page, wx, state } = setup();
  wx.showModal = (options) => options.success({ confirm: false });
  await page.startFollow();
  assert.equal(state.grants, 0);
  assert.equal(state.starts, 0);
  assert.equal(state.mic, undefined);
  assert.equal(page.data.recordStarting, false);
  assert.equal(state.toasts.length, 0);
});

test('cloud authorization failure retains the real reason and never starts microphone', async () => {
  const { page, services, state } = setup();
  services.authService.consent = async () => {
    throw new Error('登录已过期，请重新登录');
  };
  await page.startFollow();
  assert.match(page.data.recordError, /录音授权未完成.*登录已过期/);
  assert.equal(state.starts, 0);
  assert.equal(state.mic, undefined);
});

test('native modal failure displays errMsg rather than misreporting a save failure', async () => {
  const { page, wx, state } = setup();
  wx.showModal = (options) => options.fail({ errMsg: 'showModal:fail native error' });
  await page.startFollow();
  assert.match(page.data.recordError, /showModal:fail native error/);
  assert.equal(state.starts, 0);
});

test('previously denied microphone permission offers settings without starting recording', async () => {
  const { page, state } = setup();
  state.granted = true;
  state.mic = false;
  await page.startFollow();
  assert.equal(page.data.recordPermissionDenied, true);
  assert.match(page.data.recordError, /打开录音设置/);
  assert.equal(state.starts, 0);
  state.mic = true;
  await page.startFollow();
  assert.equal(state.starts, 1);
  assert.equal(page.data.recordPermissionDenied, false);
});

test('WeChat privacy errors receive a privacy-specific explanation', async () => {
  const { page, wx, state } = setup();
  state.granted = true;
  wx.authorize = (options) =>
    options.fail({ errMsg: 'authorize:fail privacy permission is not authorized' });
  await page.startFollow();
  assert.match(page.data.recordError, /微信隐私授权未完成/);
  assert.equal(page.data.recordPermissionDenied, false);
  assert.equal(state.starts, 0);
});

test('server state is checked again after consent revocation or account change', async () => {
  const { page, state } = setup();
  state.granted = true;
  await page.ensureFollowConsent();
  assert.equal(state.modals.length, 0);
  state.granted = false;
  await page.ensureFollowConsent();
  assert.equal(state.modals.length, 1);
  assert.equal(state.grants, 1);
});

test('leaving during consent check neither records nor posts a late consent', async () => {
  const { page, services, state } = setup();
  let resolvePrivacy;
  services.mineService.privacy = () =>
    new Promise((resolve) => {
      resolvePrivacy = resolve;
    });
  const start = page.startFollow();
  page.onUnload();
  resolvePrivacy({ followRecordingConsent: false });
  await start;
  assert.equal(state.starts, 0);
  assert.equal(state.grants, 0);
  assert.equal(state.modals.length, 0);
});

test('late recorder callbacks after unload cannot upload or alter the old page', async () => {
  const { page, state, events } = setup();
  await page.startFollow();
  const lateStop = events.stop;
  const lateStart = events.start;
  page.onUnload();
  lateStart();
  await lateStop({ tempFilePath: '/tmp/record.mp3', duration: 3000 });
  assert.equal(state.uploads.length, 0);
  assert.equal(events.stop, undefined);
});

test('failed uploads preserve local audio, block accidental replacement and retry the same file', async () => {
  const { page, services, state, events } = setup();
  await page.startFollow();
  events.start();
  services.contentService.uploadFollowRecording = async (...args) => {
    state.uploads.push(args);
    throw new Error('网络连接失败');
  };
  await events.stop({ tempFilePath: '/tmp/take.mp3', duration: 2400 });
  assert.equal(page.data.recordNeedsUpload, true);
  assert.equal(page.data.recordUploading, false);
  assert.equal(page.data.recordPath, '/tmp/take.mp3');
  assert.match(page.data.recordError, /录音上传失败.*网络连接失败/);
  await page.startFollow();
  assert.equal(state.starts, 1);
  services.contentService.uploadFollowRecording = async (...args) => {
    state.uploads.push(args);
    return { previewUrl: 'saved-private-url' };
  };
  await Promise.all([page.saveFollowRecording(), page.saveFollowRecording()]);
  assert.equal(state.uploads.length, 2);
  assert.deepEqual(state.uploads[0], state.uploads[1]);
  assert.equal(page.data.recordSaved, true);
  assert.equal(page.data.recordNeedsUpload, false);
  assert.equal(page.data.recordError, '');
});

test('recorder errors reset preparation state and permit another attempt', async () => {
  const { page, state, events } = setup();
  await page.startFollow();
  events.error({ errMsg: 'recorder.start:fail system permission denied' });
  assert.equal(page.data.recordStarting, false);
  assert.equal(page.data.recording, false);
  assert.match(page.data.recordError, /手机系统的微信麦克风权限/);
  assert.equal(page.data.recordPermissionDenied, true);
  await page.startFollow();
  assert.equal(state.starts, 2);
});

test('rejecting the initial microphone prompt never begins a recording', async () => {
  const { page, wx, state } = setup();
  wx.authorize = (options) => options.fail({ errMsg: 'authorize:fail auth deny' });
  await page.startFollow();
  assert.equal(state.starts, 0);
  assert.equal(page.data.recordStarting, false);
  assert.equal(page.data.recordPermissionDenied, true);
});

test('saving an existing take rechecks a revoked cloud grant before uploading', async () => {
  const { page, state } = setup();
  page.setData({ recordPath: '/tmp/take.mp3', recordNeedsUpload: true, recordDurationMs: 2400 });
  state.granted = false;
  await page.saveFollowRecording();
  assert.equal(state.grants, 1);
  assert.equal(state.uploads.length, 1);
  assert.equal(state.starts, 0);
});

test('privacy page can read and save server consent even when local storage cannot be written', async () => {
  const { services, wx, state } = setup();
  let privacy;
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, '../pages/mine/privacy/index.js'), 'utf8'),
    {
      Page: (value) => {
        privacy = value;
      },
      require: () => services,
      wx,
    },
  );
  privacy.data = { ...privacy.data };
  privacy.setData = (values) => Object.assign(privacy.data, values);
  state.granted = true;
  privacy.load();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(privacy.data.info.followRecordingConsent, true);
  privacy.saveConsent('follow_recording_upload', 'FOLLOW_RECORDING_V1', true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(privacy.data.busy, false);
  assert.equal(state.toasts.length, 0);
});
