import { createRouter, createWebHistory } from 'vue-router';
const OverviewPage = () => import('../pages/overview/index.vue');
const UsersPage = () => import('../pages/users/index.vue');
const InvitesPage = () => import('../pages/invites/index.vue');
const TopicsPage = () => import('../pages/topics/index.vue');
const ContentPage = () => import('../pages/content/index.vue');
const JobsPage = () => import('../pages/jobs/index.vue');
const AiPage = () => import('../pages/ai/index.vue');
const AiAuditPage = () => import('../pages/ai-audit/index.vue');
const AiUsagePage = () => import('../pages/ai-usage/index.vue');
const WordsPage = () => import('../pages/words/index.vue');
const ArticlesPage = () => import('../pages/articles/index.vue');
const UserLearningPage = () => import('../pages/users/learning/index.vue');
const VocabularyImportsPage = () => import('../pages/imports/index.vue');
const ParametersPage = () => import('../pages/parameters/index.vue');
const FeedbackPage = () => import('../pages/feedback/index.vue');
const StudyRecordsPage = () => import('../pages/study-records/index.vue');
const StudyRecordDetailPage = () => import('../pages/study-records/detail.vue');
const AnnouncementsPage = () => import('../pages/announcements/index.vue');
const RolesPage = () => import('../pages/roles/index.vue');

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/users' },
    { path: '/overview', component: OverviewPage, meta: { title: '概览' } },
    { path: '/content', component: ContentPage, meta: { title: '内容与来源' } },
    { path: '/announcements', component: AnnouncementsPage, meta: { title: '公告管理' } },
    { path: '/topics', component: TopicsPage, meta: { title: '主题管理' } },
    { path: '/words', component: WordsPage, meta: { title: '英语单词' } },
    {
      path: '/vocabulary-imports',
      component: VocabularyImportsPage,
      meta: { title: '词库批次自动化' },
    },
    { path: '/articles', component: ArticlesPage, meta: { title: '英语短文' } },
    { path: '/users', component: UsersPage, meta: { title: '账号列表' } },
    { path: '/invites', component: InvitesPage, meta: { title: '邀请码' } },
    {
      path: '/users/:userId/learning',
      component: UserLearningPage,
      meta: { title: '用户学习详情' },
    },
    { path: '/feedback', component: FeedbackPage, meta: { title: '用户反馈' } },
    { path: '/study-records', component: StudyRecordsPage, meta: { title: '词书学习记录' } },
    {
      path: '/study-records/:recordId',
      component: StudyRecordDetailPage,
      meta: { title: '学习记录详情' },
    },
    { path: '/jobs', component: JobsPage, meta: { title: '任务与运行' } },
    { path: '/ai', component: AiPage, meta: { title: 'AI 模型与费用' } },
    { path: '/ai-audit', component: AiAuditPage, meta: { title: 'AI 使用追溯' } },
    { path: '/ai-usage', component: AiUsagePage, meta: { title: 'AI 用量日志' } },
    { path: '/parameters', component: ParametersPage, meta: { title: '系统参数' } },
    { path: '/roles', component: RolesPage, meta: { title: '角色与权限' } },
  ],
});
