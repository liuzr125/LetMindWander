const SECTIONS = [
  ['mnemonic', '这样记', '联想是辅助，不是词源解释'],
  ['synonym', '近义与相近表达', '看使用场景，不直接画等号'],
  ['confusable', '易混词辨析', '一次对比一组，先记住关键区别'],
  ['derivative', '派生词与词族', '区分派生、屈折变化和复合词'],
  ['usage', '日常这样说', '挑一句，换成自己的生活场景'],
  ['quote', '有出处的表达', '只展示已核实来源与使用条件的内容']
];
function preferenceTypes() {
  return SECTIONS.map(([type, title]) => ({ type, title, enabled: true }));
}
function groups(cards, enabledTypes) {
  const allowed = Array.isArray(enabledTypes) ? new Set(enabledTypes) : null;
  return SECTIONS.map(([type, title, caption]) => {
    const items = (Array.isArray(cards) ? cards : []).filter(c => c.cardType === type).slice(0, 6).map(c => Object.assign({}, c, {
      sourceLabel: c.sourceKind === 'original' ? '原创学习材料 · 非影视或歌词引用' : c.sourceKind === 'content' ? '引用当前已发布词条例句' : c.sourceKind === 'reference' ? '知识点参考 · 讲解与例句为本应用编写' : '已核实出处',
      rightsLabel: c.rightsStatus === 'licensed' ? '已授权' : c.rightsStatus === 'public_domain' ? '公有领域' : c.rightsStatus === 'inherited' ? '继承词条授权状态' : '原创编写'
    }));
    return { type, title, caption, items, count: items.length, open: type === 'mnemonic' || type === 'usage', expanded: false, visibleItems: items.slice(0, 1) };
  }).filter(g => g.count && (!allowed || allowed.has(g.type)));
}
module.exports = { groups, preferenceTypes };
