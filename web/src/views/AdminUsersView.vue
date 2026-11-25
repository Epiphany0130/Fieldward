<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api, errorMessage, type Account, type UserPage } from '../api'
import { forgetSession } from '../session'

const router = useRouter()
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

onMounted(() => loadUsers())
</script>

<template>
  <main id="main" tabindex="-1" class="page">
    <h1>用户管理中心</h1>
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
