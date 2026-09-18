const { mineService } = require('../../services/index');
const { setUserInfo } = require('../../utils/storage');
Page({
 data:{overview:{},profile:{},loading:true,error:'',defaultAvatar:'/assets/logo.png',planText:'每天预计 10 分钟',planMeta:'周一至周五 · 入门'},
 onShow(){this.load();},
 load(){const token=getApp().globalData.accessToken||wx.getStorageSync('access_token');if(!token){this.setData({loading:false});return;}this.setData({loading:true,error:''});mineService.overview().then((overview)=>{const p=overview.profile||{};setUserInfo(p);this.setData({overview,profile:p,planText:`每天预计 ${overview.planMinutes||10} 分钟`,planMeta:`${this.days(overview.weekdaysMask)} · ${overview.planDifficulty==='advanced'?'进阶':'入门'}`,loading:false});}).catch((e)=>this.setData({loading:false,error:e.message||'加载失败'}));},
 days(mask){const labels=['一','二','三','四','五','六','日'],picked=labels.filter((_,i)=>(Number(mask||31)&(1<<i)));if(picked.length===5&&picked.join('')==='一二三四五')return '周一至周五';if(picked.length===7)return '每天';return picked.length?`周${picked.join('、')}`:'未设置学习日';},
 goProfile(){wx.navigateTo({url:'/pages/profile/edit/index'});},goPlan(){wx.navigateTo({url:'/pages/plan/index'});},goHistory(){wx.navigateTo({url:'/pages/journal/history/index'});},
 goFavorites(){wx.navigateTo({url:'/pages/mine/favorites/index'});},goFriends(){wx.navigateTo({url:'/pages/friends/index/index'});},
 goBookProgress(){wx.navigateTo({url:'/pages/word/books/progress/index'});},
 goWordbook(){wx.setStorageSync('learning_page_state_v1',{primaryTab:'english',englishTab:'notebook'});wx.setStorageSync('learning_deep_link','wordbook');wx.switchTab({url:'/pages/learn/index'});},
 goPrivacy(){wx.navigateTo({url:'/pages/mine/privacy/index'});},
 goFeedback(){wx.navigateTo({url:'/pages/mine/feedback/index'});},goUsage(){wx.navigateTo({url:'/pages/mine/usage/index'});}
});
