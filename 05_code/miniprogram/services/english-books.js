const { request } = require('./request');
const base = '/learning/english-books';
const key = encodeURIComponent;
module.exports = {
  list(data) {
    return request({ url: base, data });
  },
  detail(id) {
    return request({ url: `${base}/${key(id)}` });
  },
  chapter(id, cid) {
    return request({ url: `${base}/${key(id)}/chapters/${key(cid)}` });
  },
  progress(id, data) {
    return request({ url: `${base}/${key(id)}/progress`, method: 'PUT', data });
  },
  media(id, aid) {
    return request({ url: `${base}/${key(id)}/media/${key(aid)}` });
  },
  words(id, term) {
    return request({ url: `${base}/${key(id)}/words`, data: { term } });
  },
  saveWord(id, data) {
    return request({ url: `${base}/${key(id)}/word-source`, method: 'POST', data });
  },
};
