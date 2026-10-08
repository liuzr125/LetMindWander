const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function page(name, request) {
 let definition;
 vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/announcements', name, 'index.js'), 'utf8'), { require: () => ({request}), Page: value => {definition=value}, wx:{navigateTo(){},stopPullDownRefresh(){}}, Date, encodeURIComponent });
 const instance={...definition,data:{...definition.data},setData(data){Object.assign(this.data,data)}};
 return instance;
}
test('list paginates and uses Beijing publication day', async()=>{
 const calls=[];
 const p=page('list',async options=>{calls.push(options.url);return calls.length===1?Array.from({length:20},(_,i)=>({id:String(i),publishAt:'2026-10-08T18:00:00Z'})):[]});
 await p.load(true);assert.equal(p.data.items[0].date,'2026-10-09');assert.equal(p.data.hasMore,true);
 await p.load(false);assert.equal(calls[1],'/announcements?page=2&pageSize=20');assert.equal(p.data.items.length,20);assert.equal(p.data.hasMore,false);
});
test('withdrawn detail clears content and shows unavailable message',async()=>{
 const p=page('detail',async()=>{throw Object.assign(new Error('hidden'),{statusCode:404})});p.id='withdrawn';p.data.item={body:'cached body'};
 await p.load();assert.equal(p.data.item,null);assert.equal(p.data.error,'该公告已撤回或尚未发布');assert.equal(p.data.loading,false);
});
