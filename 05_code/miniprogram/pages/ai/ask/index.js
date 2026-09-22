const { aiService } = require('../../../services/index');

Page({
  data: { loading: true, sending: false, error: '', question: '', modelInfo: null, model: null, answer: null, history: [] },
  onLoad() {
    const pending = wx.getStorageSync('pendingAiQuestion');
    if (pending && pending.question) {
      this.pendingAutoSend = !!pending.autoSend;
      this.setData({ question: String(pending.question).slice(0, 8000) });
      wx.removeStorageSync('pendingAiQuestion');
    }
  },
  onShow() { this.load(); },
  load() {
    this.setData({ loading: true, error: '' });
    Promise.all([aiService.models(), aiService.history(20)]).then(([info, history]) => {
      const configured = (info.models || [])[0];
      const model = configured ? Object.assign({}, configured, { statusText: this.modelStatus(configured), displayText: `${configured.displayName} · ${String(configured.specification || '').toUpperCase()}` }) : null;
      this.setData({ modelInfo: info, model, history: history.items || [], loading: false }, () => {
        if (!this.pendingAutoSend) return;
        this.pendingAutoSend = false;
        if (info.consentGranted && model && model.available) this.send();
        else if (!info.consentGranted) wx.showToast({ title: '问一问尚未授权', icon: 'none', duration: 3000 });
      });
    }).catch((error) => this.setData({ loading: false, error: error.message || 'AI 配置加载失败' }));
  },
  modelStatus(model) {
    if (!model.credentialConfigured) return '未配置密钥';
    if (!model.priceConfigured) return '未核实价格';
    if (!model.budgetConfigured) return '未配置本月预算';
    return '可用';
  },
  inputQuestion(event) { this.setData({ question: event.detail.value }); },
  send() {
    const question = (this.data.question || '').trim(); const model = this.data.model;
    if (!question) return wx.showToast({ title: '请输入问题', icon: 'none' });
    if (!model || !model.available) return wx.showToast({ title: model ? model.statusText : '暂无可用模型', icon: 'none' });
    if (!this.data.modelInfo.consentGranted) return wx.showToast({ title: '问一问尚未授权', icon: 'none' });
    this.setData({ sending: true, answer: null });
    aiService.ask({ question }, `${Date.now()}-${Math.random().toString(36).slice(2)}`)
      .then((answer) => { this.setData({ sending: false, answer, question: '' }); return aiService.history(20); })
      .then((history) => this.setData({ history: history.items || [] }))
      .catch((error) => { this.setData({ sending: false }); wx.showToast({ title: error.message || 'AI 回答失败', icon: 'none', duration: 3000 }); });
  }
});
