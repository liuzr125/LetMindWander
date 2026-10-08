const { request } = require('../../../services/request');
Page({
  data: { item: null, loading: true, error: '' },
  onLoad(options) {
    this.id = options.id;
    this.load();
  },
  load() {
    // 只合并在途读取；重试仍获取最新发布/撤回状态。
    if (this._request) return this._request;
    this.setData({ loading: true, error: '' });
    this._request = request({ url: '/announcements/' + encodeURIComponent(this.id || '') })
      .then((item) =>
        this.setData({
          item: {
            ...item,
            date: item.publishAt
              ? new Date(new Date(item.publishAt).getTime() + 8 * 3600000)
                  .toISOString()
                  .slice(0, 10)
              : '',
          },
        }),
      )
      .catch((e) =>
        this.setData({
          item: null,
          error: e.statusCode === 404 ? '该公告已撤回或尚未发布' : e.message || '加载失败',
        }),
      )
      .finally(() => {
        this.setData({ loading: false });
        this._request = null;
      });
    return this._request;
  },
});
