<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import { account, forgetSession } from '../session'
import MentorChat from '../components/MentorChat.vue'

interface Workspace {
  conversationId: string
  createdAt: string
  latestResumeVersion: number
  resumeName: string | null
}

const router = useRouter()
const workspace = ref<Workspace>()
const loading = ref(true)
const message = ref('')
const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' })
let active = true

async function expireSession() {
  forgetSession()
  await router.replace({ name: 'login' })
}

async function loadWorkspace() {
  loading.value = true
  message.value = ''
  try {
    const state = await api<{ accountId: string; workspace: Workspace | null }>('/workspace')
    if (!active) return
    if (state.accountId !== account.value?.id) throw new ApiError(403, '登录账号已变更，请刷新页面。')
    if (!state.workspace) {
      await router.replace({ name: 'resume-draft' })
      return
    }
    workspace.value = state.workspace
  } catch (error) {
    if (!active) return
    if (error instanceof ApiError && error.status === 401) {
      await expireSession()
    } else {
      message.value = errorMessage(error)
    }
  } finally {
    if (active) loading.value = false
  }
}

onMounted(loadWorkspace)
onBeforeUnmount(() => { active = false })
</script>

<template>
  <main id="main" tabindex="-1" class="page workspace-page" :aria-busy="loading">
    <p v-if="loading" role="status" class="workspace-feedback">正在打开工作台…</p>
    <div v-else-if="message" class="surface workspace-feedback">
      <p class="notice notice-error" role="alert">{{ message }}</p>
      <button class="button button-quiet" type="button" @click="loadWorkspace">重新加载</button>
    </div>

    <template v-else-if="workspace">
      <h1>学生工作台</h1>
      <div class="workspace-layout">
        <section class="surface conversation" aria-labelledby="conversation-title">
          <header class="conversation-header">
            <span class="mentor-mark" aria-hidden="true">M</span>
            <div>
              <h2 id="conversation-title">Mentor 主对话</h2>
              <p>创建于 <time :datetime="workspace.createdAt">{{ dateFormat.format(new Date(workspace.createdAt)) }}</time></p>
            </div>
          </header>
          <MentorChat v-if="account" :key="workspace.conversationId" :account-id="account.id" @expired="expireSession" />
        </section>

        <aside class="surface workspace-resume" aria-labelledby="workspace-resume-title">
          <div>
            <h2 id="workspace-resume-title">我的简历</h2>
            <p>已提交第 {{ workspace.latestResumeVersion }} 版</p>
            <p v-if="workspace.resumeName" class="workspace-resume-name">{{ workspace.resumeName }}</p>
          </div>
          <div class="workspace-links">
            <RouterLink class="button button-quiet" :to="{ name: 'resume-draft' }">编辑简历</RouterLink>
            <RouterLink class="button button-quiet"
                        :to="{ name: 'resume-upload', query: { from: 'workspace' } }">上传新版本</RouterLink>
            <RouterLink class="button button-primary"
                        :to="{ name: 'resume-final', query: { version: String(workspace.latestResumeVersion) } }">查看成稿</RouterLink>
          </div>
        </aside>
      </div>
    </template>
  </main>
</template>

<style scoped>
.workspace-page { width: min(100% - 2rem, 71rem); }
.workspace-page h1 { margin-bottom: 1.15rem; }
.workspace-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(15rem, .38fr); gap: 1rem; align-items: start; }
.conversation { min-width: 0; min-height: 21rem; }
.conversation-header { display: flex; align-items: center; gap: .75rem; padding: 1rem; border-bottom: 1px solid #dde6dc; }
.conversation-header h2, .workspace-resume h2 { font-size: 1.05rem; }
.conversation-header p, .workspace-resume p { margin-top: .3rem; color: var(--muted); line-height: 1.55; }
.mentor-mark {
  display: grid; flex: none; place-items: center; width: 2.55rem; height: 2.55rem; border-radius: .6rem;
  background: var(--primary); color: #fff; font: 650 1.25rem/1 Georgia, serif;
}
.workspace-resume { display: grid; gap: 1rem; padding: 1rem; }
.workspace-resume-name { overflow-wrap: anywhere; }
.workspace-links { display: flex; flex-wrap: wrap; gap: .5rem; }
.workspace-links .button { display: inline-flex; align-items: center; text-decoration: none; }
.workspace-feedback { padding: 1.1rem; }
.workspace-feedback .button { margin-top: .7rem; }

@media (max-width: 680px) {
  .workspace-layout { grid-template-columns: minmax(0, 1fr); }
  .conversation { min-height: 14rem; }
}
</style>
