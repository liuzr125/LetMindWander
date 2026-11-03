const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function page(file, services, wx = {}) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    require: () => services,
    Page: (value) => {
      definition = value;
    },
    wx,
  });
  return {
    ...definition,
    data: JSON.parse(JSON.stringify(definition.data)),
    setData(values) {
      Object.assign(this.data, values);
    },
  };
}

test('saving a note or draft cannot restore old shared visibility', async () => {
  for (const state of ['active', 'draft']) {
    let saved;
    const editor = page(
      'pages/knowledge/editor/index.js',
      {
        knowledgeService: {
          create: async (payload) => {
            saved = payload;
            return { id: 'note' };
          },
        },
      },
      { showToast() {}, redirectTo() {}, switchTab() {} },
    );
    editor.data.form.visibility = 'selected';
    editor.data.form.selectedFriendIds = ['old-friend'];
    editor.save({ currentTarget: { dataset: { state } } });
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(saved.state, state);
    assert.equal(saved.visibility, 'private');
    assert.equal(saved.selectedFriendIds.length, 0);
    assert.equal(editor.openShare, undefined);
  }
});

test('note actions retain deletion without a sharing action', () => {
  let actions;
  let destination;
  const detail = page(
    'pages/knowledge/detail/index.js',
    {},
    {
      showActionSheet(value) {
        actions = value;
      },
      navigateTo(value) {
        destination = value.url;
      },
    },
  );
  detail.data.id = 'my-note';
  detail.more();
  assert.equal(actions.itemList.length, 1);
  assert.equal(actions.itemList[0], '删除知识');
  actions.success({ tapIndex: 0 });
  assert.match(destination, /knowledge\/delete\/index\?id=my-note/);
});

test('shared and friend pages are absent from the shipped page list and files', () => {
  const base = path.join(__dirname, '..');
  const app = JSON.parse(fs.readFileSync(path.join(base, 'app.json'), 'utf8'));
  assert.ok(app.pages.includes('pages/knowledge/editor/index'));
  assert.ok(!app.pages.some((name) => /pages\/friends\/|knowledge\/share/.test(name)));
  assert.ok(!fs.existsSync(path.join(base, 'pages/friends')));
  assert.ok(!fs.existsSync(path.join(base, 'pages/knowledge/share')));
});

test('restored profile drafts with public fields become private', () => {
  const profile = page('pages/profile/edit/index.js', {}, {});
  profile.data.form.profileVisibility = { introduction: 'public', real_name: 'friends' };
  profile.refreshState();
  assert.ok(
    Object.values(profile.data.form.profileVisibility).every((value) => value === 'private'),
  );
  assert.equal(profile.openVisibility, undefined);
});
