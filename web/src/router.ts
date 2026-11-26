import { createRouter, createWebHistory } from 'vue-router'
import { account, restoreSession } from './session'

export function accountHome() {
  return { name: account.value?.role === 'ADMIN' ? 'admin-users' : 'workspace' }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'login' } },
    { path: '/login', name: 'login', component: () => import('./views/AuthView.vue'), meta: { title: '登录' } },
    { path: '/register', name: 'register', component: () => import('./views/AuthView.vue'), meta: { title: '注册' } },
    { path: '/resume/edit', name: 'resume-draft', component: () => import('./views/ResumeDraftView.vue'), meta: { title: '简历制作', role: 'STUDENT' } },
    { path: '/resume/upload', name: 'resume-upload', component: () => import('./views/ResumeUploadView.vue'), meta: { title: '上传简历', role: 'STUDENT' } },
    { path: '/resume/final', name: 'resume-final', component: () => import('./views/ResumeFinalView.vue'), meta: { title: '简历成稿', role: 'STUDENT' } },
    { path: '/workspace/realtime-preview', name: 'realtime-preview', component: () => import('./components/RealtimeSession.vue'), meta: { title: '音视频设备检查', role: 'STUDENT' } },
    { path: '/workspace', name: 'workspace', component: () => import('./views/WorkspaceView.vue'), meta: { title: '学生工作台', role: 'STUDENT' } },
    { path: '/workspace/career', name: 'career-direction', component: () => import('./views/CareerDirectionView.vue'), meta: { title: '选择求职方向', role: 'STUDENT' } },
    { path: '/workspace/learning/:directionId', name: 'learning-route', component: () => import('./views/LearningRouteView.vue'), meta: { title: '学习路线', role: 'STUDENT' } },
    { path: '/workspace/career/plan/:id', name: 'career-plan', component: () => import('./views/CareerResultView.vue'), meta: { title: '匹配与学习路线', role: 'STUDENT' } },
    { path: '/workspace/career/interview/:id', name: 'career-interview', component: () => import('./views/CareerResultView.vue'), meta: { title: '模拟面试准备', role: 'STUDENT' } },
    { path: '/workspace/career/skills/:id', name: 'career-skills', component: () => import('./views/SkillSessionView.vue'), meta: { title: '技能核验旁聊', role: 'STUDENT' } },
    { path: '/admin', redirect: { name: 'admin-users' } },
    { path: '/admin/users', name: 'admin-users', component: () => import('./views/AdminUsersView.vue'), meta: { title: '用户管理中心', role: 'ADMIN' } },
    { path: '/:pathMatch(.*)*', redirect: { name: 'login' } },
  ],
})

router.beforeEach(async (to) => {
  await restoreSession()
  if (!to.meta.role) {
    return account.value ? accountHome() : true
  }
  if (!account.value) return { name: 'login' }
  return account.value.role === to.meta.role ? true : accountHome()
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? '循职'} · Fieldward`
})
