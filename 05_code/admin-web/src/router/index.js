import { createRouter, createWebHistory } from 'vue-router'
import OverviewPage from '../pages/overview/index.vue'
import UsersPage from '../pages/users/index.vue'
import ContentPage from '../pages/content/index.vue'
import JobsPage from '../pages/jobs/index.vue'
import AiPage from '../pages/ai/index.vue'
import WordsPage from '../pages/words/index.vue'
import ArticlesPage from '../pages/articles/index.vue'
import UserLearningPage from '../pages/users/learning/index.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/users' },
    { path: '/overview', component: OverviewPage, meta: { title: '概览' } },
    { path: '/content', component: ContentPage, meta: { title: '内容与来源' } },
    { path: '/words', component: WordsPage, meta: { title: '英语单词' } },
    { path: '/articles', component: ArticlesPage, meta: { title: '英语短文' } },
    { path: '/users', component: UsersPage, meta: { title: '用户状态' } },
    { path: '/users/:userId/learning', component: UserLearningPage, meta: { title: '用户学习详情' } },
    { path: '/jobs', component: JobsPage, meta: { title: '任务与运行' } },
    { path: '/ai', component: AiPage, meta: { title: 'AI 模型与费用' } }
  ]
})
