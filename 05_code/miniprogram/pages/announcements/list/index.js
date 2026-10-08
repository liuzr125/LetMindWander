const { request } = require('../../../services/request');
Page({
 data:{items:[],page:1,loading:false,hasMore:true,error:''},
 onShow(){this.load(true);},onPullDownRefresh(){this.load(true).finally(()=>wx.stopPullDownRefresh());},onReachBottom(){if(this.data.hasMore&&!this.data.loading)this.load(false);},
 load(reset){if(this.data.loading)return Promise.resolve();const page=reset?1:this.data.page;this.setData({loading:true,error:''});return request({url:`/announcements?page=${page}&pageSize=20`}).then(rows=>{const items=rows.map(item=>({...item,date:item.publishAt?new Date(new Date(item.publishAt).getTime()+8*3600000).toISOString().slice(0,10):''}));this.setData({items:reset?items:this.data.items.concat(items),page:page+1,hasMore:rows.length===20});}).catch(e=>this.setData({error:e.message||'公告加载失败'})).finally(()=>this.setData({loading:false}));},
 retry(){this.load(true);},open(e){wx.navigateTo({url:'/pages/announcements/detail/index?id='+encodeURIComponent(e.currentTarget.dataset.id)});}
});
