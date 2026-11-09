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
  },
  onLoad(options) {
    this.usesToday = !options.date;
    this.hasShown = false;
    this.setData({ date: options.date || formatDate(new Date()), from: options.from || '' });
    return this.load();
  },
  onShow() {
    if (!this.hasShown) {
      this.hasShown = true;
      return;
    }
    // An uncertain write must retry its original receipt, even after navigation.
    if (this.data.sending || this.pendingWordAttempt) return;
    if (this.usesToday) this.setData({ date: formatDate(new Date()) });
    return this.load();
  },
  load() {
    if (this.queueRequest) return this.queueRequest;
    this.queueRequest = Promise.all([
      reviewService.queue(this.data.date),
      wordStudyService.queue('review'),
    ])
      .then(([queue, study]) => {
        this.reviewRows = queue;
        this.wordStudyQueue = study;
        const words = queue.filter((item) => item.wordTerm);
        const currentWord =
          study.current && words.find((item) => item.contentId === study.current.contentId);
        const available = queue.filter((item) => !item.wordTerm);
        if (currentWord) available.unshift(currentWord);
        this.apply(available, queue.length);
        this.setData({ completed: 0, progress: 0, remainingCount: Math.max(0, queue.length - 1) });
        if (!available.length && study.remaining)
          this.setData({ error: '本轮反馈已保存，请间隔后刷新队列继续巩固' });
      })
      .catch((error) =>
        this.setData({ loading: false, error: error.message || '复习队列加载失败' }),
      )
      .finally(() => {
        this.queueRequest = null;
      });
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
    if (study.completed)
      this.reviewRows = this.reviewRows.filter((item) => item.contentId !== contentId);
    this.wordStudyQueue = study;
    const available = this.reviewRows.filter((item) => !item.wordTerm);
    const currentWord =
      study.current && this.reviewRows.find((item) => item.contentId === study.current.contentId);
    if (currentWord) available.unshift(currentWord);
    const total = this.data.total;
    const remaining =
      available.filter((item) => !item.wordTerm).length + Number(study.remaining || 0);
    this.apply(available, total);
    this.setData({
      completed: Math.max(0, total - remaining),
      remainingCount: Math.max(0, remaining - (available.length ? 1 : 0)),
      progress: total ? Math.round((Math.max(0, total - remaining) * 100) / total) : 100,
    });
    if (!available.length && study.remaining)
      this.setData({ error: '本轮反馈已保存，请间隔后刷新队列继续巩固' });
  },
  apply(queue, total) {
    const completed = Math.max(0, total - queue.length);
    this.setData({
      queue,
      current: queue[0] || null,
      total,
      completed,
      remainingCount: Math.max(0, total - 1),
      progress: total ? Math.round((completed * 100) / total) : 100,
      loading: false,
      error: '',
      answerRevealed: !(queue[0] && queue[0].wordTerm),
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
    if (this.data.sending || !this.data.current) return;
    if (!this.data.answerRevealed && !this.pendingWordAttempt) return;
    const item = this.data.current;
    const feedback = e.currentTarget.dataset.value;
    const businessToday = formatDate(new Date());
    if (
      item.wordTerm &&
      !this.pendingWordAttempt &&
      businessToday &&
      this.wordStudyQueue.businessDate !== businessToday
    ) {
      return this.load();
    }
    this.setData({ sending: true });
    let request;
    if (item.wordTerm) {
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
        if (next.businessDate && next.businessDate !== formatDate(new Date())) return this.load();
        if (!next.current && next.remaining && next.practiceMode !== 'continuous')
          return wordStudyService.queue('review').then((fresh) => this.applyWordFeedback(fresh, item.contentId));
        return this.applyWordFeedback(next, item.contentId);
      });
    } else
      request = reviewService
        .feedback(item.scheduleId, { feedback, taskId: item.taskId })
        .then(() => this.load());
    request
      .catch((error) => {
        wx.showToast({ title: error.message || '反馈失败', icon: 'none' });
        if ([400, 403, 404, 409].includes(error.statusCode || error.status)) {
          this.pendingWordAttempt = null;
          return this.load();
        }
      })
      .finally(() => this.setData({ sending: false }));
  },
});
