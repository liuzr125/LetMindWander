const { wordMemoryService } = require('../../../../services/index');
const pendingStore = require('../../../../utils/word-memory-pending');

Page({
  data: {
    sessionId: '',
    loading: true,
    error: '',
    session: null,
    current: null,
    answer: '',
    hintContent: '',
    feedback: null,
    editingRetry: false,
    acting: false,
    pendingSave: false,
    saveNotice: '',
  },
  onLoad(options) {
    const sessionId=options.id || '';
    this.pendingAttempt=pendingStore.load(sessionId);
    this.setData({ sessionId, pendingSave:!!this.pendingAttempt, answer:this.pendingAttempt?this.pendingAttempt.payload.answer:'' });
    this._shown=false;
    return this.load();
  },
  onShow() {
    if(!this._shown) {this._shown=true;return;}
    if(this.data.acting) return this._attemptRequest;
    return this.load();
  },
  load() {
    if(this.pendingAttempt) return this.submit();
    if(this._loadRequest) return this._loadRequest;
    this.setData({ loading: true, error: '' });
    this._loadRequest = wordMemoryService
      .getSession(this.data.sessionId)
      .then((session) => {
        if (session.state === 'completed' || session.state === 'partial') return this.openResult();
        const current = session.currentEpisode || null;
        if (current) current.hasClue = (current.availableHints || []).indexOf('clue') >= 0;
        this.startedAt = Date.now();
        this.setData({
          session,
          current,
          answer: current && this.rejectedAnswer && this.rejectedAnswer.episodeId===current.id ? this.rejectedAnswer.answer : '',
          hintContent: '',
          feedback: null,
          editingRetry: false,
          loading: false,
          pendingSave: false,
          saveNotice: '',
        });
        this.rejectedAnswer=null;
      })
      .catch((error) => this.setData({ loading: false, error: error.message || '训练恢复失败' }))
      .finally(()=>{this._loadRequest=null;});
    return this._loadRequest;
  },
  answerChanged(event) {
    if(this.data.acting || this.pendingAttempt || this.data.feedback) return;
    this.setData({ answer: event.detail.value });
  },
  revealHint(event) {
    if (this.data.acting || this.pendingAttempt || !this.data.current) return;
    const type = event.currentTarget.dataset.type;
    const current = this.data.current;
    this.setData({ acting: true });
    wordMemoryService
      .revealHint(this.data.sessionId, current.id, {
        hintType: type,
        expectedVersion: this.data.session.version,
      })
      .then((result) => {
        this.setData({ hintContent: result.content, 'session.version': result.sessionVersion });
      })
      .catch((error) => this.handleError(error))
      .finally(() => this.setData({ acting: false }));
  },
  submit() {
    if (this.data.acting) return this._attemptRequest;
    if(!this.pendingAttempt) {
      if(this.data.loading || !this.data.current || this.data.feedback) return;
      const answer=(this.data.answer || '').trim();
      if(!answer) return wx.showToast({title:'请先输入答案',icon:'none'});
      const current=this.data.current;
      const attemptNo=(current.attemptCount || 0)+1;
      this.pendingAttempt={sessionId:this.data.sessionId,episodeId:current.id,
        key:`memory-attempt-${this.data.sessionId}-${current.id}-${attemptNo}`,
        createdAt:Date.now(),payload:{answer,durationMs:Math.min(3600000,Math.max(0,Date.now()-this.startedAt)),expectedVersion:this.data.session.version}};
      pendingStore.save(this.pendingAttempt);
    }
    const attempt=this.pendingAttempt;
    this.setData({ acting: true });
    this.setData({pendingSave:true,saveNotice:'正在确认作答记录…'});
    this._attemptRequest = wordMemoryService
      .attempt(
        attempt.sessionId,attempt.episodeId,attempt.payload,attempt.key,
      )
      .then((result) => {
        if(!result || result.episodeId!==attempt.episodeId) throw Error('作答结果尚未确认，请重试');
        this.clearPendingAttempt();
        if(!this.data.current || this.data.current.id!==attempt.episodeId) return this.load();
        this.setData({
          feedback: result,
          editingRetry: false,
          'current.attemptCount': result.attemptNo,
          'session.version': result.sessionVersion,
          pendingSave: false,saveNotice:'',
        });
      })
      .catch((error) => {
        if([400,403,404,409].includes(error.statusCode || error.status)) {
          if((error.statusCode || error.status)===409) this.rejectedAnswer={episodeId:attempt.episodeId,answer:attempt.payload.answer};
          this.clearPendingAttempt();
          this.setData({loading:false});
        }
        else this.setData({loading:false,pendingSave:true,saveNotice:'上次答案尚未确认，请重试保存原答案后再继续'});
        return this.handleError(error);
      })
      .finally(() => {this.setData({ acting: false });this._attemptRequest=null;});
    return this._attemptRequest;
  },
  clearPendingAttempt() {
    if(this.pendingAttempt) pendingStore.clear(this.pendingAttempt.sessionId,this.pendingAttempt.key);
    this.pendingAttempt=null;
    this.setData({pendingSave:false,saveNotice:''});
  },
  editAnswer() {
    if (this.data.acting || this.pendingAttempt || !this.data.feedback || !this.data.feedback.canRetry) return;
    this.startedAt = Date.now();
    this.setData({ feedback: null, editingRetry: true });
  },
  next() {
    if(this.data.acting || this.pendingAttempt) return;
    this.load();
  },
  finish() {
    this.finishWith(false);
  },
  finishPartial() {
    if(this.data.acting || this.pendingAttempt) return;
    wx.showModal({
      title: '结束为部分完成？',
      content: '已提交作答会保留，未测维度不会计为通过。',
      success: (r) => {
        if (r.confirm) this.finishWith(true);
      },
    });
  },
  finishWith(partial) {
    if(this.pendingAttempt) return this.submit();
    if (this.data.acting) return;
    this.setData({ acting: true });
    wordMemoryService
      .finish(this.data.sessionId, { expectedVersion: this.data.session.version, partial })
      .then(() => this.openResult())
      .catch((error) => this.handleError(error))
      .finally(() => this.setData({ acting: false }));
  },
  openResult() {
    wx.redirectTo({
      url: `/pages/word/memory/result/index?id=${encodeURIComponent(this.data.sessionId)}`,
    });
  },
  handleError(error) {
    const status=error && (error.statusCode || error.status);
    if (error && (error.code === 'MEMORY_SESSION_VERSION_CONFLICT' || status===409)) {
      wx.showToast({ title: '进度已更新，正在恢复', icon: 'none' });
      return this.load();
    }
    if(status===403 || status===404) this.setData({loading:false,error:error.message || '训练会话不存在或无权访问'});
    if(status===400 && !this.data.current) return this.load();
    wx.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
  },
});
