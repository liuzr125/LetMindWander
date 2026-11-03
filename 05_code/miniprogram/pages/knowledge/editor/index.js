const { knowledgeService } = require('../../../services/index');

function emptyProblem() {
  return { phenomenon: '', environment: '', cause: '', solution: '', verification: '' };
}

Page({
  data: {
    id: '',
    parentId: '',
    sourceContentId: '',
    sourceContentVersionId: '',
    loading: false,
    saving: false,
    tagInput: '',
    suggestions: [],
    source: null,
    form: {
      itemType: 'note',
      title: '',
      body: '',
      state: 'active',
      visibility: 'private',
      selectedFriendIds: [],
      problem: emptyProblem(),
      tags: [],
      versionNo: 0,
    },
  },
  onLoad(options) {
    const id = options.id || '',
      parentId = options.parentId || '',
      sourceContentId = options.sourceContentId || '',
      sourceContentVersionId = options.sourceContentVersionId || '';
    this.setData({ id, parentId, sourceContentId, sourceContentVersionId });
    wx.setNavigationBarTitle({
      title: id ? '编辑知识' : parentId || sourceContentId ? '做笔记' : '新建知识',
    });
    knowledgeService
      .tags()
      .then((tags) => this.setData({ suggestions: tags || [] }))
      .catch(() => {});
    if (id) this.load(id);
    else if (parentId) this.loadParent(parentId);
    else if (sourceContentId) this.loadContentSource(sourceContentId);
    if (wx.enableAlertBeforeUnload)
      wx.enableAlertBeforeUnload({ message: '尚未保存，确定离开吗？' });
  },
  load(id) {
    this.setData({ loading: true });
    knowledgeService
      .get(id)
      .then((detail) =>
        this.setData({
          form: {
            itemType: detail.itemType,
            title: detail.title,
            body: detail.body,
            state: detail.state,
            visibility: 'private',
            selectedFriendIds: [],
            problem: detail.problem || emptyProblem(),
            tags: detail.tags || [],
            versionNo: detail.versionNo,
          },
          parentId: detail.noteParentId || '',
          source: detail.sourceTitle
            ? { title: detail.sourceTitle, summary: detail.sourceSummary }
            : null,
          loading: false,
        }),
      )
      .catch((error) => {
        this.setData({ loading: false });
        wx.showModal({
          title: '加载失败',
          content: error.message || '请稍后重试',
          showCancel: false,
        });
      });
  },
  loadParent(id) {
    knowledgeService
      .get(id)
      .then((detail) =>
        this.setData({
          source: { title: detail.title, summary: detail.body },
          'form.title': `关于《${detail.title}》的笔记`,
          'form.noteParentId': id,
        }),
      )
      .catch(() => {});
  },
  loadContentSource(id) {
    const { contentService } = require('../../../services/index');
    contentService
      .get(id)
      .then((detail) =>
        this.setData({
          source: { title: detail.title, summary: detail.summary || detail.body },
          sourceContentVersionId: detail.versionId,
          'form.title': `关于《${detail.title}》的笔记`,
        }),
      )
      .catch(() => wx.showToast({ title: '原文已不可用', icon: 'none' }));
  },
  selectType(event) {
    if (this.data.id) return;
    this.setData({ 'form.itemType': event.currentTarget.dataset.type });
  },
  onFieldInput(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value });
  },
  onProblemInput(event) {
    this.setData({ [`form.problem.${event.currentTarget.dataset.field}`]: event.detail.value });
  },
  onTagInput(event) {
    this.setData({ tagInput: event.detail.value });
  },
  addTag() {
    const tag = this.data.tagInput.trim();
    if (!tag) return;
    if (Array.from(tag).length > 20)
      return wx.showToast({ title: '标签最多 20 个字符', icon: 'none' });
    if (this.data.form.tags.some((item) => item.toLowerCase() === tag.toLowerCase()))
      return this.setData({ tagInput: '' });
    if (this.data.form.tags.length >= 10)
      return wx.showToast({ title: '最多添加 10 个标签', icon: 'none' });
    this.setData({ 'form.tags': this.data.form.tags.concat(tag), tagInput: '' });
  },
  addSuggestion(event) {
    this.setData({ tagInput: event.currentTarget.dataset.tag });
    this.addTag();
  },
  removeTag(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.setData({ 'form.tags': this.data.form.tags.filter((_, i) => i !== index) });
  },
  save(event) {
    if (this.data.saving) return;
    const state = event.currentTarget.dataset.state;
    const payload = {
      itemType: this.data.form.itemType,
      title: this.data.form.title,
      body: this.data.form.body,
      problem: this.data.form.problem,
      tags: this.data.form.tags,
      state,
      visibility: 'private',
      selectedFriendIds: [],
      noteParentId: this.data.parentId || this.data.form.noteParentId || null,
      sourceContentId: this.data.sourceContentId || null,
      sourceContentVersionId: this.data.sourceContentVersionId || null,
      expectedVersion: this.data.form.versionNo,
    };
    this.setData({ saving: true });
    const operation = this.data.id
      ? knowledgeService.update(this.data.id, payload)
      : knowledgeService.create(payload);
    operation
      .then((detail) => {
        if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
        wx.showToast({ title: state === 'draft' ? '草稿已保存' : '知识已保存', icon: 'success' });
        if (state === 'draft') wx.switchTab({ url: '/pages/knowledge/index' });
        else
          wx.redirectTo({
            url: `/pages/knowledge/detail/index?id=${encodeURIComponent(detail.id)}`,
          });
      })
      .catch((error) => {
        if (error.statusCode === 409)
          wx.showModal({
            title: '版本冲突',
            content: '这条知识已在其他位置更新。当前输入仍保留，请复制后重新加载服务器版本。',
            confirmText: '重新加载',
            success: (result) => {
              if (result.confirm && this.data.id) this.load(this.data.id);
            },
          });
        else wx.showToast({ title: error.message || '保存失败', icon: 'none' });
      })
      .finally(() => this.setData({ saving: false }));
  },
});
