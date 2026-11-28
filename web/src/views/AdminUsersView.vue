<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api, errorMessage, type Account, type UserPage } from '../api'
import { forgetSession } from '../session'

const router = useRouter()
const metrics = ref<{ registrations: number; diagnosisSessions: number; learningChats: number; interviewSessions: number; applications: number }>()
const search = ref('')
const query = ref('')
const result = ref<UserPage>()
const loading = ref(false)
const updating = ref('')
const message = ref('')
const feedback = ref('')
const busy = computed(() => loading.value || Boolean(updating.value))
const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' })

function formatDate(date: string) {
  return dateFormat.format(new Date(date))
}

async function showError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    forgetSession()
    await router.replace({ name: 'login' })
  } else {
    message.value = errorMessage(error)
  }
}

async function loadUsers(page = 1, nextQuery = query.value) {
  if (busy.value) return
  loading.value = true
  message.value = ''
  feedback.value = ''
  try {
    const params = new URLSearchParams({ q: nextQuery, page: String(page) })
    result.value = await api<UserPage>(`/admin/users?${params}`)
    query.value = nextQuery
  } catch (error) {
    await showError(error)
  } finally {
    loading.value = false
  }
}

async function changeStatus(user: Account) {
  if (busy.value) return
  const action = user.enabled ? '停用' : '恢复'
  const text = user.enabled
    ? `停用「${user.displayName}」？该用户将退出登录。`
    : `恢复「${user.displayName}」的登录权限？`
  if (!window.confirm(text)) return

  updating.value = user.id
  message.value = ''
  feedback.value = ''
  try {
    const updated = await api<Account>(`/admin/users/${user.id}/status`, 'PATCH', { enabled: !user.enabled })
    const row = result.value?.users.findIndex(({ id }) => id === updated.id) ?? -1
    if (row >= 0 && result.value) result.value.users.splice(row, 1, updated)
    feedback.value = `${action}成功 · ${updated.displayName}`
  } catch (error) {
    await showError(error)
  } finally {
    updating.value = ''
  }
}

async function adjustCredits(user: Account) {
  if (busy.value) return
  const raw = window.prompt(`调整「${user.displayName}」的积分（正数增加，负数扣减）`)
  if (raw === null) return
  const points = Number(raw.trim())
  if (!raw.trim() || !Number.isSafeInteger(points) || points === 0) {
    message.value = '请输入非零整数积分。'
    return
  }
  const reason = window.prompt('请输入调整原因')?.trim()
  if (!reason) return
  updating.value = user.id
  message.value = ''
  feedback.value = ''
  try {
    await api(`/admin/users/${user.id}/credits`, 'POST', { points, reason, requestId: crypto.randomUUID() })
    feedback.value = `积分调整成功 · ${user.displayName}`
  } catch (error) {
    await showError(error)
  } finally {
    updating.value = ''
  }
}

async function resetPassword(user: Account) {
  const password = window.prompt(`为「${user.displayName}」设置新密码（至少 8 字符）`)
  if (password === null) return
  try { await api(`/admin/users/${user.id}/password`, 'POST', { password }); feedback.value = '密码已重置，该账号现有会话已失效。' } catch (error) { await showError(error) }
}
async function changeRole(user: Account) {
  const role = user.role === 'ADMIN' ? 'STUDENT' : 'ADMIN'
  if (!window.confirm(`将「${user.displayName}」的角色改为${role === 'ADMIN' ? '管理员' : '学生'}？`)) return
  try { const updated = await api<Account>(`/admin/users/${user.id}/role`, 'PATCH', { role }); const row = result.value?.users.findIndex(x => x.id === user.id) ?? -1; if (row >= 0 && result.value) result.value.users.splice(row, 1, updated); feedback.value = '角色已更新。' } catch (error) { await showError(error) }
}
async function deleteUser(user: Account) {
  if (!window.confirm(`永久删除「${user.displayName}」及其数据？此操作无法撤销。`)) return
  try { await api(`/admin/users/${user.id}`, 'DELETE'); if (result.value) result.value.users = result.value.users.filter(x => x.id !== user.id); feedback.value = '账号已删除。' } catch (error) { await showError(error) }
}

onMounted(() => { loadUsers(); api<typeof metrics.value>('/admin/metrics').then(value => { metrics.value = value }).catch(showError) })
</script>

<template>
  <main id="main" tabindex="-1" class="page">
    <h1>用户管理中心</h1>
    <nav><RouterLink to="/admin/jobs">岗位管理</RouterLink> · <RouterLink to="/admin/routes">方向与路线</RouterLink></nav>
    <section v-if="metrics" class="surface"><h2>使用概况</h2><p>学生账号 {{ metrics.registrations }} · 诊断会话 {{ metrics.diagnosisSessions }} · 学习话题 {{ metrics.learningChats }} · 面试会话 {{ metrics.interviewSessions }} · 投递记录 {{ metrics.applications }}</p><p>统计为当前记录总数；实际服务成本尚待核算。</p></section>
    <section class="surface directory" aria-label="账号列表" :aria-busy="busy">
      <form class="search-form" @submit.prevent="loadUsers(1, search.trim())">
        <label class="field" for="search-users">
          <span>按姓名或邮箱搜索</span>
          <input id="search-users" v-model="search" type="search" maxlength="254" />
        </label>
        <button class="button button-primary" type="submit" :disabled="busy">搜索</button>
      </form>
      <p v-if="message" class="notice notice-error directory-notice" role="alert">{{ message }}</p>
      <p v-if="feedback" class="notice notice-success directory-notice" role="status">{{ feedback }}</p>
      <p v-if="loading" class="directory-state" role="status">正在加载用户…</p>
      <p v-else-if="result?.users.length === 0" class="directory-state" role="status">未找到符合条件的账号。</p>

      <template v-if="result?.users.length">
        <div class="user-row user-head" aria-hidden="true">
          <span>用户</span><span>角色</span><span>状态</span><span>创建日期</span><span>操作</span>
        </div>
        <ul class="user-list" aria-label="搜索结果">
          <li v-for="user in result.users" :key="user.id" class="user-row">
            <div class="user-identity">
              <strong>{{ user.displayName }}</strong>
              <span>{{ user.email }}</span>
            </div>
            <span>{{ user.role === 'ADMIN' ? '管理员' : '学生' }}</span>
            <span><span class="status-pill" :class="{ 'status-disabled': !user.enabled }">
              {{ user.enabled ? '正常' : '已停用' }}
            </span></span>
            <time :datetime="user.createdAt">{{ formatDate(user.createdAt) }}</time>
            <div class="user-actions">
              <button class="button button-quiet" type="button" :disabled="busy" @click="adjustCredits(user)">调整积分</button>
              <button class="button button-quiet" type="button" :disabled="busy" @click="resetPassword(user)">重置密码</button>
              <button class="button button-quiet" type="button" :disabled="busy" @click="changeRole(user)">修改角色</button>
              <button class="button button-quiet button-caution" type="button" :disabled="busy" @click="deleteUser(user)">删除</button>
              <button
                v-if="user.role === 'STUDENT'"
                class="button button-quiet"
                :class="{ 'button-caution': user.enabled }"
                type="button"
                :disabled="busy"
                :aria-label="`${user.enabled ? '停用' : '恢复'} ${user.displayName}`"
                @click="changeStatus(user)"
              >
                {{ updating === user.id ? '处理中…' : (user.enabled ? '停用' : '恢复') }}
              </button>
            </div>
          </li>
        </ul>
      </template>

      <nav v-if="result && (result.page > 1 || result.hasNext)" class="pagination" aria-label="用户列表分页">
        <button class="button button-quiet" type="button" :disabled="busy || result.page <= 1" @click="loadUsers(result.page - 1)">上一页</button>
        <span>第 {{ result.page }} 页</span>
        <button class="button button-quiet" type="button" :disabled="busy || !result.hasNext" @click="loadUsers(result.page + 1)">下一页</button>
      </nav>
    </section>
  </main>
</template>
