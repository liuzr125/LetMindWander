const { reviewService, wordStudyService } = require('../../services/index');
const { formatBusinessDate: formatDate } = require('../../utils/date');
Page({
  data: {
    loading: true,
    sending: false,
    date: '',
    from: '',
    queue: [],
    current: null,
    total: 0,
    completed: 0,
    remainingCount: 0,
    progress: 0,
    error: '',
    answerRevealed: false,
    pendingCount: 0,
    blockedCount: 0,
    saveNotice: '',
  },
  onLoad(options) {
    this.hasShown = false;
    this.setData({ date: formatDate(new Date()), from: options.from || '' });
    return this.load();
  },
  onShow() {
    if (!this.hasShown) {
      this.hasShown = true;
      return;
    }
    // An uncertain write must retry its original receipt, even after navigation.
    if (this.data.sending || this.pendingWordAttempt) return;
    this.setData({ date: formatDate(new Date()) });
    return this.load();
  },
  load(allowRolloverRefresh) {
    if (this.queueRequest) return this.queueRequest;
    const date=formatDate(new Date());
    this.setData({date,loading:true,error:''});
    let refreshForDate=false;
    const read=reviewService.snapshot ? reviewService.snapshot(date)
      : reviewService.queue(date).then(items=>({items,completedCount:0}));
    this.queueRequest = Promise.all([
      read,
      wordStudyService.queue('review'),
    ])
      .then(([snapshot, study]) => {
        const today=formatDate(new Date());
        if(date!==today || (snapshot.businessDate && snapshot.businessDate!==today) || (study.businessDate && study.businessDate!==today)) {
          refreshForDate=allowRolloverRefresh!==false;
          this.setData({date:today,loading:false,error:'复习日期正在同步，请稍后重试'});
          return;
        }
        this.reviewRows = (snapshot.items || []).map(item=>({...item,wordReview:item.wordReview==null?!!item.wordTerm:!!item.wordReview}));
        this.reviewCompletedCount = Number(snapshot.completedCount || 0);
        this.wordStudyQueue = study;
        this.renderQueue(study);
      })
      .catch((error) =>
        this.setData({ loading: false, error: error.message || '复习队列加载失败' }),
      )
      .finally(() => {
        this.queueRequest = null;
      })
      .then(()=>refreshForDate?this.load(false):undefined);
    return this.queueRequest;
  },
  applyWordFeedback(study, contentId) {
    // The committed response already contains the next queue and its version.
    // Keep review card metadata locally; refresh if the response needs a new card.
    if (!study || !Array.isArray(this.reviewRows)) return this.load();
    if (
      study.current &&
      !this.reviewRows.some((item) => item.contentId === study.current.contentId)
    )
      return this.load();
    if (study.completed) this.markCompleted(contentId);
    this.wordStudyQueue = study;
    this.renderQueue(study);
  },
  markCompleted(contentId) {
    if(this.reviewRows.some(item=>item.wordReview && item.contentId===contentId)) {
      this.reviewRows=this.reviewRows.filter(item=>!item.wordReview || item.contentId!==contentId);
      this.reviewCompletedCount=(this.reviewCompletedCount || 0)+1;
    }
  },
  renderQueue(study) {
    const available = this.reviewRows.filter((item) => !item.wordReview && item.available!==false);
    const currentWord =
      study.current && this.reviewRows.find((item) => item.wordReview && item.available!==false && item.contentId === study.current.contentId);
    if (currentWord) available.unshift(currentWord);
    const pendingCount=this.reviewRows.length;
    const completed=this.reviewCompletedCount || 0;
    const total=completed+pendingCount;
    const blockedCount=this.reviewRows.filter(item=>item.available===false).length;
    this.setData({
      queue:available,
      current: available[0] || null,
      total,
      completed,
      remainingCount: Math.max(0, pendingCount - (available.length?1:0)),
      pendingCount,blockedCount,
      progress: total ? Math.round((completed * 100) / total) : 0,
      loading: false,
      error: !available.length && (pendingCount>blockedCount || Number(study.remaining || 0)>0) ? '复习进度已变化，请刷新后继续' : '',
      answerRevealed: !(available[0] && available[0].wordReview),
    });
  },
  revealAnswer() {
    if (this.data.current && !this.data.sending) this.setData({ answerRevealed: true });
  },
  openKnowledge() {
    if (this.data.current)
      wx.navigateTo({
        url: `/pages/knowledge/detail/index?id=${encodeURIComponent(this.data.current.knowledgeId)}`,
      });
  },
  feedback(e) {
    if (this.data.sending) return this._feedbackRequest;
    if (!this.data.current) return;
    if (!this.data.answerRevealed && !this.pendingWordAttempt) return;
    const item = this.data.current;
    const feedback = e.currentTarget.dataset.value;
    const businessToday = formatDate(new Date());
    if (
      item.wordReview &&
      !this.pendingWordAttempt &&
      businessToday &&
      this.wordStudyQueue.businessDate !== businessToday
    ) {
      return this.load();
    }
    this.setData({ sending: true });
    let request;
    if (item.wordReview) {
      const study = this.wordStudyQueue;
      if (!this.pendingWordAttempt)
        this.pendingWordAttempt = {
          attemptId: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
          feedback,
          expectedVersion: study.current.version,
          businessDate: study.businessDate,
          mode: 'review',
        };
      request = wordStudyService.feedback(item.contentId, this.pendingWordAttempt).then((next) => {
        this.pendingWordAttempt = null;
        this.setData({saveNotice:''});
        if (next.businessDate && next.businessDate !== formatDate(new Date())) return this.load();
        if (!next.current && next.remaining && next.practiceMode !== 'continuous') {
          if(next.completed) this.markCompleted(item.contentId);
          return wordStudyService.queue('review').then((fresh) => this.applyWordFeedback(fresh, item.contentId));
        }
        return this.applyWordFeedback(next, item.contentId);
      });
    } else
      request = reviewService
        .feedback(item.scheduleId, { feedback, taskId: item.taskId })
        .then(() => this.load());
    this._feedbackRequest = request
      .catch((error) => {
        wx.showToast({ title: error.message || '反馈失败', icon: 'none' });
        if ([400, 403, 404, 409].includes(error.statusCode || error.status)) {
          this.pendingWordAttempt = null;
          this.setData({saveNotice:''});
          return this.load();
        }
        if(this.pendingWordAttempt) this.setData({saveNotice:'上次反馈尚未确认，再次点击会重试原反馈'});
      })
      .finally(() => {this.setData({ sending: false });this._feedbackRequest=null;});
    return this._feedbackRequest;
  },
});
