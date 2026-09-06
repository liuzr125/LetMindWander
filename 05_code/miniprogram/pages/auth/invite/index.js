const { authService } = require('../../../services/index');
const { setAccessToken, setUserInfo, setRegistrationContext } = require('../../../utils/storage');

function wxLogin() {
  return new Promise((resolve, reject) => wx.login({ success: resolve, fail: reject }));
}

function getProfile() {
  if (!wx.getUserProfile) return Promise.resolve(null);
  return new Promise((resolve) => {
    wx.getUserProfile({
      desc: '用于完善注册昵称与头像',
      success: (result) => resolve(result.userInfo || null),
      fail: () => resolve(null)
    });
  });
}

Page({
  data: {
    topInset: 44,
    inviteCode: '',
    privacyAccepted: false,
    canSubmit: false,
    loading: false,
    errorMessage: ''
  },

  onLoad() {
    const draft = wx.getStorageSync('f01_invite_draft') || {};
    let topInset = 44;
    try {
      const menu = wx.getMenuButtonBoundingClientRect();
      topInset = menu.bottom + 12;
    } catch (error) {}
    this.setData({
      topInset,
      inviteCode: draft.inviteCode || '',
      privacyAccepted: Boolean(draft.privacyAccepted)
    }, () => this.refreshState());
    // 静默登录：先查 openid，老用户直接免输入邀请码进入小程序。
    this.silentLogin();
  },

  async silentLogin() {
    if (this.silentChecking) return;
    this.silentChecking = true;
    this.setData({ loading: true, errorMessage: '' }, () => this.refreshState());
    try {
      const loginResult = await wxLogin();
      if (!loginResult.code) return;
      const result = await authService.wechatLogin({
        code: loginResult.code,
        privacyVersion: getApp().globalData.privacyVersion
      });
      if (result.stage === 'authenticated') {
        setAccessToken(result.accessToken);
        setUserInfo(result.user);
        wx.removeStorageSync('f01_invite_draft');
        wx.switchTab({ url: '/pages/today/index' });
      }
      // 其余情况（registration_required / 需要邀请码）：留在本页继续输入邀请码。
    } catch (error) {
      // 新用户或网络异常时静默失败，不打扰用户，由手动提交时再给出提示。
    } finally {
      this.silentChecking = false;
      // 静默检查结束：不是老用户，才展示邀请码卡片。
      this.setData({ loading: false }, () => this.refreshState());
    }
  },

  onShow() {
    if (this.silentChecking || !getApp().globalData.accessToken || this.sessionChecked) return;
    this.sessionChecked = true;
    authService.me().then((user) => {
      setUserInfo(user);
      wx.switchTab({ url: '/pages/today/index' });
    }).catch(() => {
      setAccessToken('');
      setUserInfo(null);
    });
  },

  onHide() { this.persistDraft(); },
  onUnload() { this.persistDraft(); },

  persistDraft() {
    wx.setStorageSync('f01_invite_draft', {
      inviteCode: this.data.inviteCode,
      privacyAccepted: this.data.privacyAccepted
    });
  },

  handleInviteInput(event) {
    this.setData({ inviteCode: (event.detail.value || '').toUpperCase(), errorMessage: '' }, () => this.refreshState());
  },

  togglePrivacy() {
    this.setData({ privacyAccepted: !this.data.privacyAccepted, errorMessage: '' }, () => this.refreshState());
  },

  refreshState() {
    this.setData({ canSubmit: Boolean(this.data.inviteCode.trim() && this.data.privacyAccepted && !this.data.loading) });
  },

  openAgreement() {
    wx.showModal({
      title: '用户协议',
      content: '继续使用即表示你同意遵守账号、内容和服务使用规则。正式发布前请在此接入已审核的完整协议文本。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  openPrivacy() {
    if (wx.openPrivacyContract) {
      wx.openPrivacyContract({ fail: () => this.showPrivacyFallback() });
      return;
    }
    this.showPrivacyFallback();
  },

  showPrivacyFallback() {
    wx.showModal({
      title: '隐私说明',
      content: '登录阶段仅使用微信身份和邀请码完成准入；手机号仅在注册确认页由你主动授权。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },

  async submit() {
    if (!this.data.canSubmit || this.data.loading) return;
    this.setData({ loading: true, errorMessage: '' }, () => this.refreshState());
    try {
      const [loginResult, profile] = await Promise.all([wxLogin(), getProfile()]);
      if (!loginResult.code) throw new Error('未获取到微信登录凭证');
      const result = await authService.wechatLogin({
        code: loginResult.code,
        inviteCode: this.data.inviteCode.trim(),
        privacyVersion: getApp().globalData.privacyVersion
      });
      if (result.stage === 'authenticated') {
        setAccessToken(result.accessToken);
        setUserInfo(result.user);
        wx.removeStorageSync('f01_invite_draft');
        wx.switchTab({ url: '/pages/today/index' });
        return;
      }
      setRegistrationContext({
        ticket: result.registrationTicket,
        expiresAt: result.expiresAt,
        profile: profile || result.profile || {},
        privacyVersion: getApp().globalData.privacyVersion
      });
      wx.navigateTo({ url: '/pages/auth/register/index' });
    } catch (error) {
      const messages = {
        INVITE_UNAVAILABLE: '邀请码无效、已过期或已被使用，请核对后重试。',
        ADMISSION_FULL: '受邀名额已满，暂时无法加入。',
        PRIVACY_CONSENT_REQUIRED: '请先阅读并同意用户协议和隐私说明。',
        ACCOUNT_UNAVAILABLE: '账号当前不可用，请联系管理员。'
      };
      this.setData({ errorMessage: messages[error.code] || error.message || '登录失败，请稍后重试' });
    } finally {
      this.setData({ loading: false }, () => this.refreshState());
    }
  }
});
