const { profileService } = require('../../../services/index');
const { setUserInfo } = require('../../../utils/storage');
const LEVELS = ['private', 'friends', 'public'];
const LABELS = ['仅自己可见', '好友可见', '公开'];
const FIELDS = [
  ['real_name', '真实姓名'], ['english_name', '英语名字'], ['birthday', '生日'],
  ['gender', '性别'], ['hobbies', '爱好'], ['introduction', '自我介绍']
];
const count = value => Array.from(value || '').length;
const copy = value => JSON.parse(JSON.stringify(value));

Page({
  data: {
    form: { nickname: '', avatarUrl: '', realName: '', englishName: '', birthday: '', gender: 0, hobbies: [], introduction: '', profileVisibility: {} },
    rules: {}, genderOptions: ['未设置', '男', '女', '其他'], visibilityOptions: LABELS,
    visibilityRows: [], visibilityLabel: '仅自己可见', mobileMasked: '', shortId: '',
    introLength: 0, hobbyInput: '', today: '', loading: true, saving: false, uploading: false,
    dirty: false, canSubmit: false, errorMessage: '', avatarSheet: false, visibilitySheet: false,
    avatarPreview: '', candidate: '', confirmAvatar: false, statusBarHeight: 20, navHeight: 44
  },
  onLoad() {
    const token = getApp().globalData.accessToken || wx.getStorageSync('access_token');
    if (!token) {
      wx.redirectTo({ url: '/pages/auth/invite/index' });
      return;
    }
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    const menu = wx.getMenuButtonBoundingClientRect();
    this.setData({ statusBarHeight: info.statusBarHeight, navHeight: (menu.top - info.statusBarHeight) * 2 + menu.height });
    this.loadProfile();
  },
  loadProfile() {
    this.setData({ loading: true, errorMessage: '' });
    return Promise.all([profileService.get(), profileService.rules()]).then(([profile, rules]) => {
      this.profile = profile;
      this.draftKey = `f02_profile_draft_${profile.id}`;
      this.setData({ rules, today: rules.today, loading: false });
      this.applySaved(profile);
      const draft = wx.getStorageSync(this.draftKey);
      if (draft && draft.form) {
        wx.showModal({ title: '恢复未保存的资料？', content: draft.rowVersion === profile.rowVersion ? '可以继续编辑上次保留的草稿。' : '已保存资料有更新。请先查看新资料，再重新填写未保存内容。',
          confirmText: draft.rowVersion === profile.rowVersion ? '恢复草稿' : '查看最新', cancelText: '放弃草稿',
          success: res => {
            if (res.confirm && draft.rowVersion === profile.rowVersion) {
              this.setData({ form: copy(draft.form), dirty: true, avatarPreview: draft.form.avatarUrl || '' }, () => this.refreshState());
            } else wx.removeStorageSync(this.draftKey);
          }
        });
      }
    }).catch(error => {
      if (error.statusCode === 401) {
        wx.reLaunch({ url: '/pages/auth/invite/index' });
        return;
      }
      this.setData({ loading: false, errorMessage: error.message || '资料加载失败，请重试' });
    });
  },
  applySaved(profile) {
    this.profile = profile;
    const profileVisibility = {};
    FIELDS.forEach(([key]) => { profileVisibility[key] = (profile.profileVisibility || {})[key] || 'private'; });
    this.setData({
      form: { nickname: profile.nickname || '', avatarUrl: profile.avatarUrl || '', realName: profile.realName || '',
        englishName: profile.englishName || '', birthday: profile.birthday || '', gender: profile.gender || 0,
        hobbies: profile.hobbies || [], introduction: profile.introduction || '', profileVisibility },
      avatarPreview: profile.avatarUrl || '', shortId: profile.shortId || '', mobileMasked: profile.mobileMasked || '',
      dirty: false, errorMessage: ''
    }, () => this.refreshState());
    setUserInfo(profile);
  },
  refreshState() {
    const { form, rules } = this.data;
    const levels = FIELDS.map(([key]) => form.profileVisibility[key] || 'private');
    this.setData({
      introLength: count(form.introduction),
      visibilityLabel: levels.every(v => v === levels[0]) ? LABELS[LEVELS.indexOf(levels[0])] : '按字段设置',
      visibilityRows: FIELDS.map(([key, label]) => ({ key, label, index: LEVELS.indexOf(form.profileVisibility[key] || 'private') })),
      canSubmit: !!this.profile && !this.data.loading && !this.data.saving && !this.data.uploading &&
        count(form.nickname.trim()) > 0 && count(form.nickname.trim()) <= rules.nickname
    });
    if (this.data.dirty && wx.enableAlertBeforeUnload) {
      wx.enableAlertBeforeUnload({ message: '资料尚未保存，退出后将保留草稿。' });
    } else if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
  },
  onField(e) {
    const key = e.currentTarget.dataset.field;
    if (!['nickname', 'realName', 'englishName', 'introduction'].includes(key)) return;
    this.setData({ [`form.${key}`]: e.detail.value, dirty: true }, () => this.refreshState());
  },
  onBirthday(e) { this.setData({ 'form.birthday': e.detail.value, dirty: true }, () => this.refreshState()); },
  clearBirthday() { this.setData({ 'form.birthday': '', dirty: true }, () => this.refreshState()); },
  onGender(e) { this.setData({ 'form.gender': Number(e.detail.value), dirty: true }, () => this.refreshState()); },
  onHobbyInput(e) { this.setData({ hobbyInput: e.detail.value }); },
  addHobby() {
    const hobby = this.data.hobbyInput.trim();
    const { hobbies } = this.data.form;
    if (!hobby) return;
    if (count(hobby) > this.data.rules.hobbyLength) return this.toast(`每个爱好最多 ${this.data.rules.hobbyLength} 个字符`);
    if (hobbies.includes(hobby)) return this.toast('该爱好已添加');
    if (hobbies.length >= this.data.rules.hobbyCount) return this.toast(`最多 ${this.data.rules.hobbyCount} 项爱好`);
    this.setData({ 'form.hobbies': hobbies.concat(hobby), hobbyInput: '', dirty: true }, () => this.refreshState());
  },
  removeHobby(e) {
    this.setData({ 'form.hobbies': this.data.form.hobbies.filter((_, index) => index !== Number(e.currentTarget.dataset.index)), dirty: true }, () => this.refreshState());
  },
  openVisibility() { this.setData({ visibilitySheet: true, visibilityDraft: copy(this.data.form.profileVisibility) }); },
  onVisibility(e) {
    this.setData({ [`visibilityDraft.${e.currentTarget.dataset.field}`]: LEVELS[Number(e.detail.value)] });
    this.setData({ visibilityRows: FIELDS.map(([key, label]) => ({ key, label, index: LEVELS.indexOf(this.data.visibilityDraft[key] || 'private') })) });
  },
  confirmVisibility() {
    this.setData({ 'form.profileVisibility': copy(this.data.visibilityDraft), dirty: true, visibilitySheet: false }, () => this.refreshState());
  },
  cancelVisibility() { this.setData({ visibilitySheet: false }, () => this.refreshState()); },
  changeAvatar() { if (!this.data.loading && !this.data.uploading && !this.data.saving) this.setData({ avatarSheet: true }); },
  closeAvatar() { this.setData({ avatarSheet: false, confirmAvatar: false, candidate: '' }); },
  noop() {},
  chooseAvatar(e) {
    const source = e.currentTarget.dataset.source;
    this.setData({ avatarSheet: false });
    if (source === 'default') return this.setData({ candidate: '', confirmAvatar: true });
    wx.chooseMedia({ count: 1, mediaType: ['image'], sourceType: [source], sizeType: ['original'],
      success: media => {
        const file = media.tempFiles && media.tempFiles[0];
        if (!file) return;
        if (file.size > this.data.rules.avatarBytes) return this.toast('图片不能超过 5MB');
        wx.getImageInfo({ src: file.tempFilePath,
          success: info => {
            if (!['jpeg', 'jpg', 'png', 'webp'].includes((info.type || '').toLowerCase())) return this.toast('仅支持 JPEG/PNG/WebP 格式');
            this.setData({ candidate: file.tempFilePath, confirmAvatar: true });
          }, fail: () => this.toast('无法读取图片，请重新选择')
        });
      }, fail: error => { if (!/cancel/.test(error.errMsg || '')) this.toast('无法打开相机或相册，请检查权限'); }
    });
  },
  confirmAvatar() {
    if (this.data.uploading) return;
    const candidate = this.data.candidate;
    if (!candidate) {
      this.setData({ 'form.avatarUrl': '', avatarPreview: '', confirmAvatar: false, dirty: true }, () => this.refreshState());
      return;
    }
    this.setData({ uploading: true }, () => this.refreshState());
    profileService.uploadAvatar(candidate).then(result => {
      // Upload only changes the draft. All profile changes are committed together by Save.
      this.setData({ 'form.avatarUrl': result.url, avatarPreview: candidate, dirty: true, confirmAvatar: false, uploading: false }, () => this.refreshState());
    }).catch(error => {
      this.setData({ uploading: false }, () => this.refreshState());
      this.toast(error.message || '上传失败，请重试');
    });
  },
  validate() {
    const { form, rules, today } = this.data;
    for (const [key, label] of [['nickname', '昵称'], ['realName', '真实姓名'], ['englishName', '英语名字'], ['introduction', '自我介绍']]) {
      if (count(form[key].trim()) > rules[key]) return `${label}最多 ${rules[key]} 个字符`;
    }
    if (!form.nickname.trim()) return '昵称不能为空';
    if (form.birthday && form.birthday > today) return '生日不能晚于今天';
    return '';
  },
  save() {
    if (!this.data.canSubmit) return;
    const error = this.validate();
    if (error) return this.setData({ errorMessage: error });
    this.setData({ saving: true, errorMessage: '' }, () => this.refreshState());
    const payload = copy(this.data.form);
    payload.rowVersion = this.profile.rowVersion;
    payload.avatarUrl = payload.avatarUrl || null;
    return profileService.update(payload).then(profile => {
      wx.removeStorageSync(this.draftKey);
      this.setData({ saving: false });
      this.applySaved(profile);
      this.toast('已保存');
      wx.switchTab({ url: '/pages/mine/index' });
    }).catch(error => {
      this.setData({ saving: false, errorMessage: error.message || '保存失败，请重试', conflict: error.code === 'PROFILE_VERSION_CONFLICT' }, () => this.refreshState());
    });
  },
  leave() {
    if (this.data.saving || this.data.uploading) return this.toast('请等待当前操作完成');
    if (!this.data.dirty) return this.goBack();
    wx.showModal({ title: '资料尚未保存', content: '保留草稿以便下次继续，或放弃本次修改。', confirmText: '保留草稿', cancelText: '放弃修改',
      success: res => {
        if (res.confirm) this.persistDraft();
        else if (res.cancel) { wx.removeStorageSync(this.draftKey); this.setData({ dirty: false }); }
        else return;
        this.goBack();
      }
    });
  },
  goBack() {
    if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
    wx.switchTab({ url: '/pages/mine/index' });
  },
  persistDraft() {
    if (this.data.dirty && this.draftKey) wx.setStorageSync(this.draftKey, { form: copy(this.data.form), rowVersion: this.profile.rowVersion });
  },
  onHide() { this.persistDraft(); },
  onUnload() { this.persistDraft(); },
  toast(title) { wx.showToast({ title, icon: 'none' }); }
});
