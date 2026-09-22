import { createRouter, createWebHistory } from 'vue-router'
import OverviewPage from '../pages/overview/index.vue'
import UsersPage from '../pages/users/index.vue'
import ContentPage from '../pages/content/index.vue'
import JobsPage from '../pages/jobs/index.vue'
import AiPage from '../pages/ai/index.vue'
import AiAuditPage from '../pages/ai-audit/index.vue'
import AiUsagePage from '../pages/ai-usage/index.vue'
import WordsPage from '../pages/words/index.vue'
import ArticlesPage from '../pages/articles/index.vue'
import UserLearningPage from '../pages/users/learning/index.vue'
import VocabularyImportsPage from '../pages/imports/index.vue'
import ParametersPage from '../pages/parameters/index.vue'
import FeedbackPage from '../pages/feedback/index.vue'
import StudyRecordsPage from '../pages/study-records/index.vue'
import StudyRecordDetailPage from '../pages/study-records/detail.vue'
import RolesPage from '../pages/roles/index.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/users' },
    { path: '/overview', component: OverviewPage, meta: { title: '概览' } },
    { path: '/content', component: ContentPage, meta: { title: '内容与来源' } },
    { path: '/words', component: WordsPage, meta: { title: '英语单词' } },
    { path: '/vocabulary-imports', component: VocabularyImportsPage, meta: { title: '词库批次自动化' } },
    { path: '/articles', component: ArticlesPage, meta: { title: '英语短文' } },
    { path: '/users', component: UsersPage, meta: { title: '用户状态' } },
    { path: '/users/:userId/learning', component: UserLearningPage, meta: { title: '用户学习详情' } },
    { path: '/feedback', component: FeedbackPage, meta: { title: '用户反馈' } },
    { path: '/study-records', component: StudyRecordsPage, meta: { title: '词书学习记录' } },
    { path: '/study-records/:ownerId/:bookId', component: StudyRecordDetailPage, meta: { title: '学习记录详情' } },
    { path: '/jobs', component: JobsPage, meta: { title: '任务与运行' } },
    { path: '/ai', component: AiPage, meta: { title: 'AI 模型与费用' } },
    { path: '/ai-audit', component: AiAuditPage, meta: { title: 'AI 使用追溯' } },
    { path: '/ai-usage', component: AiUsagePage, meta: { title: 'AI 用量日志' } },
    { path: '/parameters', component: ParametersPage, meta: { title: '系统参数' } },
    { path: '/roles', component: RolesPage, meta: { title: '角色与权限' } }
  ]
})
