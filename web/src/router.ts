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
    { path: '/workspace', name: 'workspace', component: () => import('./views/WorkspaceView.vue'), meta: { title: '学生工作台', role: 'STUDENT' } },
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
