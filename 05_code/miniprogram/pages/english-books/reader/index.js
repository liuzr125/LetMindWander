const books = require('../../../services/english-books');
const tokenize = (text) =>
  (text.match(/[A-Za-z]+(?:['’-][A-Za-z]+)*|[^A-Za-z]+/g) || []).map((text) => ({
    text,
    word: /^[A-Za-z]/.test(text),
  }));
Page({
  data: {
    book: null,
    chapter: null,
    chapterIndex: 0,
    loading: true,
    error: '',
    directory: true,
    showPictures: true,
    showTranslation: false,
    fontSize: 36,
    playing: false,
    buffering: false,
    audioError: '',
    seconds: 0,
    duration: 0,
    elapsedText: '00:00',
    durationText: '00:00',
    sliderValue: 0,
    speed: 1,
    speeds: [0.75, 1, 1.25, 1.5],
    continuous: true,
    syncError: '',
    word: null,
    wordLoading: false,
    wordError: '',
    wordResults: [],
    savingWord: false,
    progress: { revision: 0, completed: [] },
    completedCount: 0,
  },
  onLoad(options) {
    this.bookId = options.id;
    this.audio = wx.createInnerAudioContext();
    this.audio.obeyMuteSwitch = false;
    this.audio.onPlay(() => {
      this.switchingAudio = false;
      this.setData({ playing: true, buffering: false });
    });
    this.audio.onPause(() => {
      this.setData({ playing: false });
      if (!this.switchingAudio) this.saveProgress();
    });
    this.audio.onWaiting(() => this.setData({ buffering: true }));
    this.audio.onCanplay(() => {
      if (this.restoreSeconds != null) {
        this.audio.seek(this.restoreSeconds);
        this.restoreSeconds = null;
      }
      this.audio.playbackRate = this.data.speed;
    });
    this.audio.onTimeUpdate(() => this.timeUpdate());
    this.audio.onEnded(() => this.audioEnded());
    this.audio.onError(() => this.audioFailed());
    this.load();
  },
  onShow() {
    this.hidden = false;
  },
  onHide() {
    this.hidden = true;
    if (this.audio) this.audio.pause();
    this.saveProgress();
  },
  onUnload() {
    this.dead = true;
    this.loadSeq = (this.loadSeq || 0) + 1;
    this.playSeq = (this.playSeq || 0) + 1;
    clearTimeout(this.scrollTimer);
    this.saveProgress();
    if (this.audio) this.audio.destroy();
  },
  async load() {
    this.setData({ loading: true, error: '' });
    try {
      const book = await books.detail(this.bookId);
      book.chapters = book.chapters.map((c) => ({
        ...c,
        completed: (book.progress.completed || []).includes(c.id),
      }));
      if (this.dead) return;
      this.setData({
        book,
        progress: book.progress,
        completedCount: (book.progress.completed || []).length,
      });
      this.progressRevision = book.progress.revision;
      const owner =
        (getApp().globalData.userInfo || {}).userId ||
        (wx.getStorageSync('user_info') || {}).userId;
      // Access token separates local pending records even if a user profile has not loaded.
      this.pendingKey = `english_book_pending:${owner || getApp().globalData.accessToken || wx.getStorageSync('access_token')}:${this.bookId}`;
      const pending = wx.getStorageSync(this.pendingKey);
      this.conflicted = false;
      if (pending) {
        if (pending.revision === this.progressRevision) this.unsynced = pending;
        else this.conflicted = true;
      }
      if (pending)
        this.setData({
          syncError: '有上次未同步的位置，可点击重试同步；其他设备已更新时需重新加载。',
        });
      const pos = book.progress.reading || {};
      let index = book.chapters.findIndex((c) => c.id === pos.chapterId);
      if (index < 0) index = 0;
      if (!book.chapters.length) throw new Error('书籍暂无可读章节');
      await this.loadChapter(index, pos.paragraphId);
    } catch (e) {
      if (!this.dead) this.setData({ error: e.message || '书籍加载失败' });
    } finally {
      if (!this.dead) this.setData({ loading: false });
    }
  },
  async loadChapter(index, paragraphId, autoplay = false) {
    const seq = (this.loadSeq = (this.loadSeq || 0) + 1);
    this.playSeq = (this.playSeq || 0) + 1;
    this.audio.stop();
    this.setData({ playing: false, buffering: false, audioError: '', word: null, seconds: 0 });
    const chapter = await books.chapter(this.bookId, this.data.book.chapters[index].id);
    if (this.dead || seq !== this.loadSeq) return;
    chapter.paragraphs = chapter.paragraphs.map((p) =>
      Object.assign({}, p, { tokens: tokenize(p.text) }),
    );
    this.offsets = [];
    let duration = 0;
    chapter.audio.forEach((c) => {
      this.offsets.push(duration);
      duration += c.durationSeconds;
    });
    this.clip = 0;
    this.reading = { chapterId: chapter.id, paragraphId: paragraphId || chapter.paragraphs[0].id };
    this.listening = null;
    this.setData({
      chapter,
      chapterIndex: index,
      duration,
      durationText: this.clock(duration),
      seconds: 0,
      sliderValue: 0,
      elapsedText: '00:00',
    });
    for (let i = 0; i < chapter.pictures.length; i++) {
      books
        .media(this.bookId, chapter.pictures[i].assetId)
        .then((r) => {
          if (!this.dead && seq === this.loadSeq)
            this.setData({ [`chapter.pictures[${i}].url`]: r.url });
        })
        .catch(() => {
          if (!this.dead && seq === this.loadSeq)
            this.setData({ [`chapter.pictures[${i}].failed`]: true });
        });
    }
    if (paragraphId)
      wx.nextTick(() => wx.pageScrollTo({ selector: `#p-${paragraphId}`, duration: 0 }));
    if (autoplay && !this.hidden) await this.startClip(0, 0);
  },
  clock(seconds) {
    const s = Math.max(0, Math.floor(seconds || 0));
    return `${Math.floor(s / 60)
      .toString()
      .padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;
  },
  toggleDirectory() {
    this.setData({ directory: !this.data.directory });
  },
  async selectChapter(e) {
    const index = Number(e.currentTarget.dataset.index);
    await this.saveProgress();
    try {
      await this.loadChapter(index);
      this.setData({ directory: false });
    } catch (e) {
      this.setData({ error: e.message });
    }
  },
  async changeChapter(e) {
    const index = this.data.chapterIndex + Number(e.currentTarget.dataset.delta);
    if (index < 0 || index >= this.data.book.chapters.length) return;
    await this.saveProgress();
    try {
      await this.loadChapter(index);
      wx.pageScrollTo({ scrollTop: 0, duration: 0 });
    } catch (e) {
      this.setData({ error: e.message });
    }
  },
  togglePictures() {
    this.setData({ showPictures: !this.data.showPictures });
  },
  toggleTranslation() {
    this.setData({ showTranslation: !this.data.showTranslation });
  },
  changeSize(e) {
    this.setData({
      fontSize: Math.min(
        48,
        Math.max(28, this.data.fontSize + Number(e.currentTarget.dataset.delta)),
      ),
    });
  },
  previewPicture(e) {
    const url = e.currentTarget.dataset.url;
    if (url)
      wx.previewImage({
        current: url,
        urls: this.data.chapter.pictures.map((p) => p.url).filter(Boolean),
      });
  },
  pictureError(e) {
    this.setData({ [`chapter.pictures[${e.currentTarget.dataset.index}].failed`]: true });
  },
  async retryPictures() {
    const seq = this.loadSeq;
    for (let i = 0; i < this.data.chapter.pictures.length; i++) {
      try {
        const r = await books.media(this.bookId, this.data.chapter.pictures[i].assetId);
        if (!this.dead && seq === this.loadSeq)
          this.setData({
            [`chapter.pictures[${i}].url`]: r.url,
            [`chapter.pictures[${i}].failed`]: false,
          });
      } catch {}
    }
  },
  async toggleAudio() {
    if (this.data.playing) {
      this.audio.pause();
      return;
    }
    const remembered = this.data.progress.listening;
    if (
      !this.listening &&
      remembered &&
      remembered.chapterId === this.data.chapter.id &&
      this.data.chapter.audio[remembered.clip]?.key === remembered.audioKey
    )
      await this.startClip(remembered.clip, remembered.seconds);
    else await this.startClip(this.clip || 0, this.listening ? this.listening.seconds : 0);
  },
  async continueListening() {
    const pos = this.data.progress.listening;
    if (!pos) {
      await this.toggleAudio();
      return;
    }
    const index = this.data.book.chapters.findIndex((c) => c.id === pos.chapterId);
    if (index < 0) return;
    await this.saveProgress();
    if (index !== this.data.chapterIndex) await this.loadChapter(index);
    if (this.data.chapter.audio[pos.clip]?.key !== pos.audioKey) {
      this.setData({ audioError: '音频版本已更新，请从本章重新播放。' });
      return;
    }
    this.setData({ directory: false });
    await this.startClip(pos.clip, pos.seconds);
  },
  async startClip(index, seconds) {
    const seq = (this.playSeq = (this.playSeq || 0) + 1),
      chapter = this.data.chapter,
      clip = chapter.audio[index];
    if (!clip || this.dead || this.hidden) return;
    this.switchingAudio = true;
    this.audio.pause();
    this.clip = index;
    this.listening = {
      chapterId: chapter.id,
      clip: index,
      seconds: seconds || 0,
      audioKey: clip.key,
    };
    this.setData({
      buffering: true,
      audioError: '',
      seconds: (this.offsets[index] || 0) + (seconds || 0),
      elapsedText: this.clock((this.offsets[index] || 0) + (seconds || 0)),
    });
    try {
      const r = await books.media(this.bookId, clip.assetId);
      if (this.dead || this.hidden || seq !== this.playSeq) return;
      this.restoreSeconds = seconds || 0;
      this.audio.startTime = seconds || 0;
      this.audio.src = r.url;
      this.audio.playbackRate = this.data.speed;
      this.audio.play();
    } catch (e) {
      if (!this.dead && seq === this.playSeq)
        this.setData({
          buffering: false,
          playing: false,
          audioError: e.message || '音频暂不可用，点击重试',
        });
    }
  },
  async audioFailed() {
    if (this.dead || this.hidden) return;
    this.setData({ playing: false, buffering: false });
    if (!this.refreshedAudio) {
      this.refreshedAudio = true;
      await this.startClip(this.clip, this.listening?.seconds || 0);
    } else this.setData({ audioError: '音频加载失败，检查网络后点击重试。' });
  },
  retryAudio() {
    this.refreshedAudio = false;
    this.startClip(this.clip, this.listening?.seconds || 0);
  },
  timeUpdate() {
    if (this.dead || this.switchingAudio || !this.data.chapter) return;
    const local = this.audio.currentTime || 0;
    const clip = this.data.chapter.audio[this.clip];
    if (!clip) return;
    this.listening = {
      chapterId: this.data.chapter.id,
      clip: this.clip,
      seconds: local,
      audioKey: clip.key,
    };
    const seconds = (this.offsets[this.clip] || 0) + local;
    this.setData({
      seconds,
      sliderValue: this.data.duration
        ? Math.min(1000, Math.round((seconds / this.data.duration) * 1000))
        : 0,
      elapsedText: this.clock(seconds),
      buffering: false,
    });
    if (Date.now() - (this.lastSaved || 0) > 10000) {
      this.lastSaved = Date.now();
      this.saveProgress();
    }
  },
  async audioEnded() {
    if (this.dead || this.hidden) return;
    if (this.clip + 1 < this.data.chapter.audio.length) {
      await this.startClip(this.clip + 1, 0);
      return;
    }
    this.setData({ playing: false, buffering: false });
    await this.saveProgress();
    if (this.data.continuous && this.data.chapterIndex + 1 < this.data.book.chapters.length) {
      try {
        await this.loadChapter(this.data.chapterIndex + 1, null, true);
      } catch (e) {
        this.setData({ audioError: e.message });
      }
    }
  },
  seek(e) {
    const seconds = (Number(e.detail.value) / 1000) * this.data.duration;
    let index = 0;
    for (let i = 0; i < this.offsets.length; i++) if (this.offsets[i] <= seconds) index = i;
    this.refreshedAudio = false;
    this.startClip(index, Math.max(0, seconds - this.offsets[index]));
  },
  speedChange(e) {
    const speed = this.data.speeds[Number(e.detail.value)] || 1;
    this.audio.playbackRate = speed;
    this.setData({ speed });
  },
  toggleContinuous() {
    this.setData({ continuous: !this.data.continuous });
  },
  onPageScroll() {
    clearTimeout(this.scrollTimer);
    this.scrollTimer = setTimeout(() => {
      if (this.dead || this.data.directory) return;
      wx.createSelectorQuery()
        .selectAll('.paragraph')
        .boundingClientRect((rects) => {
          const p = (rects || []).find((r) => r.bottom > 100);
          if (p) this.reading = { chapterId: this.data.chapter.id, paragraphId: p.id.slice(2) };
        })
        .exec();
    }, 350);
  },
  saveProgress(extra = {}) {
    if (!this.data.book || !this.pendingKey) return Promise.resolve();
    const update = { ...(this.unsynced || {}), ...extra };
    if (this.reading) update.reading = { ...this.reading };
    if (this.listening) update.listening = { ...this.listening };
    this.saveChain = (this.saveChain || Promise.resolve())
      .catch(() => {})
      .then(async () => {
        if (this.conflicted) return;
        const data = { ...(this.unsynced || {}), ...update, revision: this.progressRevision };
        this.unsynced = { ...data };
        wx.setStorageSync(this.pendingKey, data);
        try {
          const progress = await books.progress(this.bookId, data);
          this.progressRevision = progress.revision;
          this.unsynced = null;
          wx.removeStorageSync(this.pendingKey);
          if (!this.dead)
            this.setData({
              progress,
              completedCount: progress.completed.length,
              'book.chapters': this.data.book.chapters.map((c) => ({
                ...c,
                completed: progress.completed.includes(c.id),
              })),
              syncError: '',
            });
        } catch (e) {
          if (e.statusCode === 409) this.conflicted = true;
          if (!this.dead)
            this.setData({
              syncError:
                e.statusCode === 409
                  ? '其他设备或书籍版本已更新，请重新加载后继续。'
                  : '位置尚未同步，已在本机保留，点击重试同步。',
            });
        }
      });
    return this.saveChain;
  },
  async retrySync() {
    if (this.conflicted) {
      this.conflicted = false;
      await this.load();
      return;
    }
    const saved = wx.getStorageSync(this.pendingKey);
    if (saved) {
      if (saved.revision !== this.progressRevision) {
        this.setData({ syncError: '待同步记录版本已变化，请重新加载。' });
        this.conflicted = true;
        return;
      }
      await this.saveProgress(saved);
    } else await this.saveProgress();
  },
  async complete() {
    await this.saveProgress({ completeChapterId: this.data.chapter.id });
    if (!this.data.syncError) wx.showToast({ title: '本章已完成', icon: 'success' });
  },
  async addShelf() {
    await this.saveProgress();
    if (!this.data.syncError) wx.showToast({ title: '已加入书架', icon: 'success' });
  },
  async selectWord(e) {
    const { term, pid } = e.currentTarget.dataset;
    const seq = (this.wordSeq = (this.wordSeq || 0) + 1);
    this.setData({
      word: { term, paragraphId: pid },
      wordLoading: true,
      wordError: '',
      wordResults: [],
    });
    try {
      const r = await books.words(this.bookId, term);
      if (this.dead || seq !== this.wordSeq) return;
      const choices = r.items.filter(
        (w) => (w.wordTerm || '').toLowerCase() === term.toLowerCase() || w.aliasMatch,
      );
      this.setData({
        wordResults: choices,
        wordError: choices.length ? '' : '词典中暂未匹配到此词，可先继续阅读。',
      });
    } catch (e) {
      if (!this.dead && seq === this.wordSeq) this.setData({ wordError: e.message });
    } finally {
      if (!this.dead && seq === this.wordSeq) this.setData({ wordLoading: false });
    }
  },
  closeWord() {
    this.wordSeq = (this.wordSeq || 0) + 1;
    this.setData({ word: null });
  },
  async saveWord(e) {
    if (this.data.savingWord) return;
    this.setData({ savingWord: true });
    try {
      await books.saveWord(this.bookId, {
        term: this.data.word.term,
        chapterId: this.data.chapter.id,
        paragraphId: this.data.word.paragraphId,
        contentId: e.currentTarget.dataset.id,
      });
      wx.showToast({ title: '已加入生词本', icon: 'success' });
    } catch (e) {
      this.setData({ wordError: e.message });
    } finally {
      if (!this.dead) this.setData({ savingWord: false });
    }
  },
  openWord(e) {
    wx.navigateTo({
      url: `/pages/word/detail/index?id=${encodeURIComponent(e.currentTarget.dataset.id)}`,
    });
  },
});
