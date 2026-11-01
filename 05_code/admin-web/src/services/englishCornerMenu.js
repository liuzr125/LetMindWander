export const englishMenuCodes = ['words', 'imports', 'articles', 'study_records'];
const groupCode = 'group_english';

// Also groups older server menu data, so an existing session need not gain new page permissions.
export function withEnglishCorner(items) {
  if (!items.some((item) => englishMenuCodes.includes(item.code))) return items;
  const existing = items.find((item) => item.code === groupCode);
  const group = {
    ...existing,
    code: groupCode,
    path: '',
    parentCode: null,
    icon: 'En',
    name: '英语角',
    description: '英语单词、词库批次、英语短文与词书学习记录',
  };
  const result = items.map((item) => {
    if (item.code === groupCode) return group;
    if (englishMenuCodes.includes(item.code)) return { ...item, parentCode: groupCode };
    if (item.code === 'group_content') return { ...item, description: '技术内容、主题、公告与来源管理' };
    return item;
  });
  if (!existing) {
    const usersIndex = result.findIndex((item) => item.code === 'group_users');
    const contentIndex = result.findIndex((item) => item.code === 'group_content');
    result.splice(usersIndex >= 0 ? usersIndex : contentIndex + 1, 0, group);
  }
  return result;
}

export function visibleNavigationMenus(items, codes = []) {
  const allowed = new Set(codes);
  const hasEnglishPage = items.some((item) => englishMenuCodes.includes(item.code) && allowed.has(item.code));
  return items.filter((item) => {
    if (item.code === groupCode) return hasEnglishPage;
    if (!allowed.has(item.code)) return false;
    if (!item.path) return items.some((child) => child.parentCode === item.code && allowed.has(child.code));
    return true;
  });
}
