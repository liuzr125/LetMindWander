const test = require('node:test');
const assert = require('node:assert/strict');
const helpers = import('../src/services/englishCornerMenu.js');
const oldMenus = [
  { code: 'overview', path: '/overview' },
  { code: 'group_content', path: '' },
  { code: 'content', path: '/content', parentCode: 'group_content' },
  { code: 'words', path: '/words', parentCode: 'group_content' },
  { code: 'imports', path: '/vocabulary-imports', parentCode: 'group_content' },
  { code: 'articles', path: '/articles', parentCode: 'group_content' },
  { code: 'english_books', path: '/english-books', parentCode: 'group_content' },
  { code: 'group_users', path: '' },
  { code: 'users', path: '/users', parentCode: 'group_users' },
  { code: 'study_records', path: '/study-records', parentCode: 'group_users' },
  { code: 'group_system', path: '' },
  { code: 'jobs', path: '/jobs', parentCode: 'group_system' },
];

test('groups English pages while preserving page paths and old server menu input', async () => {
  const { withEnglishCorner, englishMenuCodes } = await helpers;
  const original = JSON.stringify(oldMenus);
  const menus = withEnglishCorner(oldMenus);
  assert.equal(menus.find((item) => item.code === 'group_english').name, '英语角');
  assert.ok(
    menus.findIndex((item) => item.code === 'group_english') <
      menus.findIndex((item) => item.code === 'group_users'),
  );
  for (const code of englishMenuCodes) {
    const item = menus.find((item) => item.code === code);
    assert.equal(item.parentCode, 'group_english');
    assert.equal(item.path, oldMenus.find((item) => item.code === code).path);
  }
  assert.equal(menus.find((item) => item.code === 'jobs').parentCode, 'group_system');
  assert.equal(JSON.stringify(oldMenus), original);
});

test('an article-only role sees the English container but no unauthorized sibling pages or empty old groups', async () => {
  const { withEnglishCorner, visibleNavigationMenus } = await helpers;
  const visible = visibleNavigationMenus(withEnglishCorner(oldMenus), [
    'articles',
    'group_content',
  ]);
  assert.deepEqual(visible.map((item) => item.code).sort(), ['articles', 'group_english']);
});

test('a parent-only or non-English role never gains English page visibility', async () => {
  const { withEnglishCorner, visibleNavigationMenus } = await helpers;
  const menus = withEnglishCorner(oldMenus);
  assert.deepEqual(visibleNavigationMenus(menus, ['group_english']), []);
  assert.deepEqual(
    visibleNavigationMenus(menus, ['group_users', 'users']).map((item) => item.code),
    ['group_users', 'users'],
  );
  assert.deepEqual(visibleNavigationMenus(menus), []);
});

test('upgraded menu data retains one English group and its existing database id', async () => {
  const { withEnglishCorner } = await helpers;
  const upgraded = [...oldMenus, { id: 'existing-group', code: 'group_english', path: '' }];
  const result = withEnglishCorner(withEnglishCorner(upgraded));
  assert.equal(result.filter((item) => item.code === 'group_english').length, 1);
  assert.equal(result.find((item) => item.code === 'group_english').id, 'existing-group');
});

test('book-only roles see the reading book page without word or article permissions', async () => {
  const { withEnglishCorner, visibleNavigationMenus } = await helpers;
  const visible = visibleNavigationMenus(withEnglishCorner(oldMenus), ['english_books']);
  assert.deepEqual(visible.map((item) => item.code).sort(), ['english_books', 'group_english']);
});
