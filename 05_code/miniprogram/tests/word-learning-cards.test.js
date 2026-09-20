const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { groups, preferenceTypes } = require('../utils/word-learning-cards');
const studyPreferences = require('../utils/word-study-preferences');
test('groups separate synonyms, confusables and family; initial view is bounded', () => {
  const cards = ['confusable','synonym','derivative','usage','usage','mnemonic'].map((cardType,id) => ({ cardType, id, sourceKind:'original', rightsStatus:'original' }));
  const result = groups(cards);
  assert.deepEqual(result.map(g => g.type), ['mnemonic','synonym','confusable','derivative','usage']);
  assert.equal(result.find(g=>g.type==='confusable').open, false);
  assert.equal(result.find(g=>g.type==='usage').visibleItems.length, 1);
  assert.equal(result.find(g=>g.type==='usage').count, 2);
  assert.equal(cards[0].sourceLabel, undefined);
  assert.deepEqual(groups(null), []);
});
test('linked published examples are clearly distinguished from original material', () => {
  const item=groups([{ id:'linked',cardType:'usage',sourceKind:'content',rightsStatus:'inherited' }])[0].items[0];
  assert.equal(item.sourceLabel,'引用当前已发布词条例句');
  assert.equal(item.rightsLabel,'继承词条授权状态');
});
test('user card preferences can hide selected sections without mutating cards', () => {
  const cards=['mnemonic','synonym','usage'].map((cardType,id)=>({cardType,id}));
  const result=groups(cards,['mnemonic','usage']);
  assert.deepEqual(result.map(group=>group.type),['mnemonic','usage']);
  assert.equal(cards.length,3);
  assert.equal(preferenceTypes().length,6);
});
function page() {
  let definition;
  vm.runInNewContext(fs.readFileSync(require.resolve('../pages/word/detail/index.js'),'utf8'), {
    Page: value => { definition=value; },
    require: path => path.includes('word-learning-cards') ? { groups, preferenceTypes } : path.includes('word-study-preferences') ? studyPreferences : path.includes('/date') ? { formatDate:()=>'' } : {},
    wx:{ pageScrollTo:()=>{} }, console
  });
  definition.data=Object.assign({},definition.data,definition.supplementDefaults);
  definition.setData=function(value){Object.assign(this.data,value);};
  return definition;
}
test('recall hides supplemental content and stops audio without recording an answer', () => {
  const p=page();let stopped=0;p.stopAudio=()=>stopped++;
  p.toggleRecall();assert.equal(p.data.recallActive,true);
  assert.doesNotThrow(()=>p.rateWord({currentTarget:{dataset:{value:'remember'}}}));
  p.toggleRecall();assert.equal(p.data.recallActive,false);assert.equal(stopped,2);
  assert.equal(p.data.acting,false);
});
test('accordion and show-more only alter selected group', () => {
  const p=page();p.data.learningCardGroups=groups([{id:'1',cardType:'usage'},{id:'2',cardType:'usage'},{id:'3',cardType:'confusable'}]);
  const event={currentTarget:{dataset:{type:'usage'}}};
  p.expandCardGroup(event);assert.equal(p.data.learningCardGroups.find(g=>g.type==='usage').visibleItems.length,2);
  p.expandCardGroup(event);assert.equal(p.data.learningCardGroups.find(g=>g.type==='usage').visibleItems.length,1);
  p.toggleCardGroup(event);assert.equal(p.data.learningCardGroups.find(g=>g.type==='usage').open,false);
  assert.equal(p.data.learningCardGroups.find(g=>g.type==='confusable').open,false);
});
test('collapsed learning cards use compact state styles without changing expanded content markup', () => {
  const markup=fs.readFileSync(require.resolve('../pages/word/detail/index.wxml'),'utf8');
  const styles=fs.readFileSync(require.resolve('../pages/word/detail/index.wxss'),'utf8');
  assert.match(markup,/learning-card-group \{\{group\.open \? 'is-open' : 'is-collapsed'\}\}/);
  assert.match(styles,/\.learning-card-group\.is-collapsed\{/);
  assert.match(styles,/\.learning-card-group\.is-collapsed \.memory-caption\{/);
  assert.match(markup,/<block wx:if="\{\{group\.open\}\}">[\s\S]*?class="learning-card-item"/);
});
test('all translations, hints and cards are inside recall-hidden block', () => {
  const markup=fs.readFileSync(require.resolve('../pages/word/detail/index.wxml'),'utf8');
  const start=markup.indexOf('<block wx:if="{{!recallActive}}">');
  const end=markup.lastIndexOf('</block>',markup.indexOf('<view class="bottom-actions">'));
  for(const token of ['{{detail.meaning}}','{{displayExample}}','{{learningCardGroups}}','{{memoryHints}}']) {
    assert.ok(markup.indexOf(token)>start && markup.indexOf(token)<end,token);
  }
  assert.match(markup,/disabled="\{\{acting \|\| recallActive\}\}"/);
});
test('new-word preferences default to visible British audio three times with a 1.5 second gap', () => {
  const value=studyPreferences.normalize();
  assert.deepEqual({mode:value.defaultAnswerMode,enabled:value.autoPlayEnabled,accent:value.autoPlayAccent,count:value.autoPlayCount,interval:value.autoPlayIntervalMs},{mode:'visible',enabled:true,accent:'uk',count:3,interval:1500});
  assert.equal(studyPreferences.nextPlaybackDelay(1,3,1500),1500);
  assert.equal(studyPreferences.nextPlaybackDelay(2,3,1500),1500);
  assert.equal(studyPreferences.nextPlaybackDelay(3,3,1500),null);
});
test('word detail opens a dedicated settings page for study and card preferences', () => {
  const detailMarkup=fs.readFileSync(require.resolve('../pages/word/detail/index.wxml'),'utf8');
  const settingsMarkup=fs.readFileSync(require.resolve('../pages/word/settings/index.wxml'),'utf8');
  const appConfig=JSON.parse(fs.readFileSync(require.resolve('../app.json'),'utf8'));
  assert.match(detailMarkup,/bindtap="openLearningSettings"/);
  assert.doesNotMatch(detailMarkup,/新词学习设置/);
  assert.doesNotMatch(detailMarkup,/学习卡片展示/);
  for(const handler of ['changeDefaultHidden','changeAutoPlayEnabled','changeStudyAccent','changeStudyCount','changeStudyInterval','changeCardPreference']) assert.match(settingsMarkup,new RegExp(handler));
  assert.ok(appConfig.pages.includes('pages/word/settings/index'));
  assert.deepEqual(studyPreferences.countOptions(),[1,2,3,4,5]);
  assert.deepEqual(studyPreferences.intervalOptions().map(item=>item.value),[1000,1500,2000]);
});
