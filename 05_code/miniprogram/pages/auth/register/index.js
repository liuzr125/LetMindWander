const { authService } = require('../../../services/index');
const { setAccessToken, setUserInfo, setRegistrationContext, getRegistrationContext } = require('../../../utils/storage');

Page({
  data: {
    topInset: 44,
    registration: null,
    nickname: '学习者',
    avatarUrl: '',
    mobile: '',
    smsCode: '',
    smsSending: false,
    smsCountdown: 0,
    privacyAccepted: true,
    canSubmit: false,
    loading: false,
    fieldError: '',
    errorMessage: ''
  },

  onUnload() {
    if (this.smsTimer) clearInterval(this.smsTimer);
  },

  onLoad() {
    const registration = getRegistrationContext();
    if (!registration || !registration.ticket) {
      wx.showModal({ title: '注册凭证已失效', content: '请返回受邀页重新登录。', showCancel: false, success: () => wx.navigateBack() });
      return;
    }
    let topInset = 44;
    try {
      const menu = wx.getMenuButtonBoundingClientRect();
      topInset = menu.top;
    } catch (error) {}
    const profile = registration.profile || {};
    this.setData({
      topInset,
      registration,
      nickname: (profile.nickName || profile.nickname || '学习者').slice(0, 30),
      avatarUrl: profile.avatarUrl || ''
    }, () => this.refreshState());
  },

  goBack() { wx.navigateBack(); },

  handleNickname(event) {
    const nickname = event.detail.value || '';
    this.setData({ nickname, fieldError: '', errorMessage: '' }, () => this.refreshState());
  },

  changeAvatar(event) {
    const avatarUrl = event && event.detail && event.detail.avatarUrl;
    if (!avatarUrl) {
      this.setData({ errorMessage: '未选择头像，请重新尝试。' });
      return;
    }
    // chooseAvatar returns a local temporary path. It is immediately previewed;
    // production persistence is handled by the object-storage upload flow.
    this.setData({ avatarUrl, errorMessage: '' });
  },

  handleMobile(event) {
    const mobile = String(event.detail.value || '').replace(/\D/g, '').slice(0, 11);
    this.setData({ mobile, smsCode: '', errorMessage: '' }, () => this.refreshState());
  },

  handleSmsCode(event) {
    const smsCode = String(event.detail.value || '').replace(/\D/g, '').slice(0, 6);
    this.setData({ smsCode, errorMessage: '' }, () => this.refreshState());
  },

  async sendSmsCode() {
    if (this.data.smsSending || this.data.smsCountdown > 0) return;
    if (!/^1[3-9]\d{9}$/.test(this.data.mobile)) {
      this.setData({ errorMessage: '请输入正确的11位手机号。' });
      return;
    }
    this.setData({ smsSending: true, errorMessage: '' });
    try {
      const result = await authService.sendSmsCode({
        registrationTicket: this.data.registration.ticket,
        mobile: this.data.mobile
      });
      this.startSmsCountdown(Number(result.retryAfter) || 60);
      // mock 模式下后端会返回验证码，直接弹窗展示（开发阶段没有真实短信）。
      if (result.code) {
        wx.showModal({
          title: '短信验证码',
          content: `验证码：${result.code}\n（开发阶段模拟短信，${result.expiresIn || 300} 秒内有效）`,
          showCancel: false,
          confirmText: '填入',
          success: (res) => {
            if (res.confirm) {
              this.setData({ smsCode: String(result.code) }, () => this.refreshState());
            }
          }
        });
      } else {
        wx.showToast({ title: '验证码已发送', icon: 'success' });
      }
    } catch (error) {
      const messages = {
        SMS_NOT_CONFIGURED: '短信服务尚未配置，请联系管理员。',
        SMS_CODE_RATE_LIMITED: error.message || '操作过于频繁，请稍后重试。',
        REGISTRATION_TICKET_EXPIRED: '注册凭证已过期，请返回重新登录。'
      };
      this.setData({ errorMessage: messages[error.code] || error.message || '验证码发送失败，请稍后重试。' });
    } finally {
      this.setData({ smsSending: false });
    }
  },

  startSmsCountdown(seconds) {
    if (this.smsTimer) clearInterval(this.smsTimer);
    this.setData({ smsCountdown: seconds });
    this.smsTimer = setInterval(() => {
      const next = this.data.smsCountdown - 1;
      if (next <= 0) {
        clearInterval(this.smsTimer);
        this.smsTimer = null;
        this.setData({ smsCountdown: 0 });
      } else {
        this.setData({ smsCountdown: next });
      }
    }, 1000);
  },

  togglePrivacy() {
    this.setData({ privacyAccepted: !this.data.privacyAccepted }, () => this.refreshState());
  },

  openAgreement() {
    wx.showModal({ title: '用户协议', content: '继续注册即表示你同意账号和服务使用规则。', showCancel: false });
  },

  openPrivacy() {
    if (wx.openPrivacyContract) {
      wx.openPrivacyContract({ fail: () => wx.showModal({ title: '隐私政策', content: '手机号仅用于账号绑定，注册后仅脱敏展示。', showCancel: false }) });
    } else {
      wx.showModal({ title: '隐私政策', content: '手机号仅用于账号绑定，注册后仅脱敏展示。', showCancel: false });
    }
  },

  refreshState() {
    const nickname = this.data.nickname.trim();
    const validNickname = nickname.length > 0 && Array.from(nickname).length <= 30;
    const validMobile = /^1[3-9]\d{9}$/.test(this.data.mobile);
    const validSmsCode = /^\d{6}$/.test(this.data.smsCode);
    this.setData({ canSubmit: Boolean(validNickname && validMobile && validSmsCode && this.data.privacyAccepted && !this.data.loading) });
  },

  async submit() {
    if (!this.data.canSubmit || this.data.loading) return;
    this.setData({ loading: true, errorMessage: '', fieldError: '' }, () => this.refreshState());
    try {
      const result = await authService.register({
        registrationTicket: this.data.registration.ticket,
        mobile: this.data.mobile,
        smsCode: this.data.smsCode,
        nickname: this.data.nickname.trim(),
        avatarUrl: /^https:\/\//.test(this.data.avatarUrl) ? this.data.avatarUrl : '',
        privacyVersion: this.data.registration.privacyVersion || getApp().globalData.privacyVersion,
        aiConsent: false
      });
      setAccessToken(result.accessToken);
      setUserInfo(result.user);
      setRegistrationContext(null);
      wx.removeStorageSync('f01_invite_draft');
      wx.switchTab({ url: '/pages/today/index' });
    } catch (error) {
      if (error.code === 'INVALID_NICKNAME') {
        this.setData({ fieldError: '昵称需为 1 至 30 个字符' });
      } else {
        const messages = {
          SMS_CODE_REQUIRED: '请先获取短信验证码。',
          SMS_CODE_INVALID: '短信验证码不正确。',
          SMS_CODE_EXPIRED: '短信验证码已过期，请重新获取。',
          SMS_CODE_ATTEMPTS_EXCEEDED: '验证码错误次数过多，请重新获取。',
          INVITE_UNAVAILABLE: '邀请码已失效，请返回受邀页重新获取。',
          ADMISSION_FULL: '受邀名额已满，暂时无法完成注册。',
          REGISTRATION_TICKET_EXPIRED: '注册凭证已过期，请返回重新登录。',
          ACCOUNT_UNAVAILABLE: '账号当前不可用，请联系管理员。'
        };
        this.setData({ errorMessage: messages[error.code] || error.message || '注册失败，请稍后重试' });
      }
    } finally {
      this.setData({ loading: false }, () => this.refreshState());
    }
  }
});
