const { planService } = require('../../../services/index');

const DAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

Page({
  data: { plans: [] },
  onShow() { this.loadHistory(); },
  loadHistory() {
    planService.history().then((plans) => this.setData({ plans: (plans || []).map((plan) => Object.assign({}, plan, {
      weekdaysText: this.weekdays(plan.weekdaysMask),
      difficultyText: plan.difficulty === 'advanced' ? '进阶' : '入门',
      topicsText: (plan.topics || []).map((topic) => topic.name).join('、') || '未选择',
      reviewText: plan.reviewEnabled ? `复习上限 ${plan.reviewLimit} 项` : '未开启复习'
    })) })).catch((error) => wx.showToast({ title: error.message || '加载计划失败', icon: 'none' }));
  },
  weekdays(mask) {
    const days = DAY_NAMES.filter((name, index) => mask & (1 << index));
    return days.length ? days.join('、') : '无学习日';
  }
});
