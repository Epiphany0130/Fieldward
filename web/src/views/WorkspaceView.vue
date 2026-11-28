<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import { account, forgetSession } from '../session'
import MentorChat from '../components/MentorChat.vue'
import type { SideChat } from '../mentor'
import type { CareerAssessment } from '../career'

interface Workspace {
  conversationId: string
  createdAt: string
  latestResumeVersion: number
  resumeName: string | null
  careerOnboardingRequired: boolean
}

const router = useRouter()
const route = useRoute()
const workspace = ref<Workspace>()
const career = ref<CareerAssessment | null>(null)
const nextStep = ref('')
const report = computed(() => career.value?.report)
const interviewReady = computed(() => report.value?.stage === 'INTERVIEW_READY')
const firstStep = computed(() => report.value?.roadmap[0])
const loading = ref(true)
const message = ref('')
const sides = ref<SideChat[]>([])
const sideTitle = ref('')
const sideError = ref('')
const creatingSide = ref(false)
const selectedSide = computed(() => sides.value.find(side => side.id === route.query.side && !side.verificationId))
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
    const state = await api<{ accountId: string; workspace: Workspace | null; career: CareerAssessment | null; nextStep: string | null }>('/workspace')
    if (!active) return
    if (state.accountId !== account.value?.id) throw new ApiError(403, '登录账号已变更，请刷新页面。')
    if (!state.workspace) {
      await router.replace({ name: 'resume-draft' })
      return
    }
    if (state.workspace.careerOnboardingRequired && state.career?.status !== 'READY') {
      await router.replace({ name: 'career-direction' })
      return
    }
    workspace.value = state.workspace
    career.value = state.career
    nextStep.value = state.nextStep ?? ''
    const channels = await api<{ accountId: string; sides: SideChat[] }>('/mentor/side-chats')
    if (!active) return
    if (channels.accountId !== account.value?.id) throw new ApiError(403, '登录账号已变更，请刷新页面。')
    sides.value = channels.sides
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

async function createSide() {
  if (!account.value || creatingSide.value || !sideTitle.value.trim()) return
  creatingSide.value = true
  sideError.value = ''
  try {
    const result = await api<SideChat>('/mentor/side-chats', 'POST', { accountId: account.value.id, title: sideTitle.value.trim() })
    if (!active) return
    if (!sides.value.some(({ id }) => id === result.id)) sides.value.unshift(result)
    sideTitle.value = ''
    await router.push({ name: 'workspace', query: { side: result.id } })
  } catch (error) {
    if (active && error instanceof ApiError && error.status === 401) await expireSession()
    else if (active) sideError.value = errorMessage(error)
  } finally {
    if (active) creatingSide.value = false
  }
}

onMounted(loadWorkspace)
onBeforeUnmount(() => { active = false })
</script>

<template>
  <main id="main" tabindex="-1" class="page workspace-page" :aria-busy="loading">
    <RouterLink to="/workspace/credits">我的积分</RouterLink>
    <p v-if="loading" role="status" class="workspace-feedback">正在打开工作台…</p>
    <div v-else-if="message" class="surface workspace-feedback">
      <p class="notice notice-error" role="alert">{{ message }}</p>
      <button class="button button-quiet" type="button" @click="loadWorkspace">重新加载</button>
    </div>

    <template v-else-if="workspace">
      <h1>学生工作台</h1>
      <RouterLink to="/workspace/jobs">浏览岗位</RouterLink>
      <section v-if="career && !report" class="surface fit-banner" aria-label="当前方向">
        <div>
          <h2>{{ career.directionTitle }}</h2>
          <p>{{ career.status === 'FAILED' ? '分析未完成，可以重试。' : career.status === 'ANALYZING' ? '正在分析简历。' : '等待继续分析。' }}</p>
          <RouterLink class="button button-quiet" :to="{ name: 'career-direction', query: { change: '1' } }">查看方向与分析状态</RouterLink>
        </div>
      </section>
      <section v-if="career && report" class="surface fit-banner" aria-labelledby="fit-heading">
        <div>
          <h2 id="fit-heading">{{ career.directionTitle }}</h2>
          <p v-if="report.matchPercent !== null">岗位方向匹配度 <strong>{{ report.matchPercent }}%</strong></p>
          <p v-else>简历暂未评分 · 已生成基础计划</p>
        </div>
        <div class="fit-next">
          <strong v-if="report.route">先确认路线上的技能，再由 Mentor 核验和带学</strong>
          <strong v-else-if="interviewReady">已进入模拟面试准备</strong>
          <template v-else-if="firstStep">
            <strong>首个学习任务 · {{ firstStep.title }}</strong>
            <p>{{ firstStep.task }}</p>
          </template>
          <p v-if="career.resumeVersion !== workspace.latestResumeVersion">
            当前计划依据第 {{ career.resumeVersion }} 版简历。
          </p>
        </div>
        <div class="workspace-links">
          <RouterLink class="button button-primary"
                      :to="{ name: !report.route && interviewReady ? 'career-interview' : 'career-plan', params: { id: career.id } }">
            {{ !report.route && interviewReady ? '查看面试准备' : '查看匹配与学习路线' }}
          </RouterLink>
          <RouterLink class="button button-quiet" :to="{ name: 'career-direction', query: { change: '1' } }">更换方向或重评</RouterLink>
          <RouterLink v-if="career.needsReanalysis" class="button button-quiet"
                      :to="{ name: 'career-direction', query: { reanalyze: career.directionId } }">使用新版 AI 分析</RouterLink>
        </div>
      </section>
      <p v-if="nextStep" class="surface workspace-guidance">Mentor · {{ nextStep }}</p>
      <div class="workspace-layout">
        <section class="surface conversation" aria-labelledby="conversation-title">
          <header class="conversation-header">
            <span class="mentor-mark" aria-hidden="true">M</span>
            <div>
              <h2 id="conversation-title">{{ selectedSide ? selectedSide.title : 'Mentor 主对话' }}</h2>
              <p v-if="selectedSide">旁聊 · 与主对话共享记忆</p>
              <p v-else>创建于 <time :datetime="workspace.createdAt">{{ dateFormat.format(new Date(workspace.createdAt)) }}</time></p>
            </div>
          </header>
          <KeepAlive :max="8">
            <MentorChat v-if="account" :key="`${workspace.conversationId}:${selectedSide?.id ?? 'main'}`"
                        :account-id="account.id" :side-id="selectedSide?.id ?? null" @expired="expireSession" />
          </KeepAlive>
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
            <RouterLink v-if="!report" class="button button-quiet"
                        :to="{ name: 'career-direction', query: { change: '1' } }">选择求职方向</RouterLink>
          </div>
          <section class="side-chats" aria-labelledby="side-heading">
            <h2 id="side-heading">对话</h2>
            <nav aria-label="切换 Mentor 对话">
              <RouterLink :to="{ name: 'workspace' }" :aria-current="selectedSide ? undefined : 'page'">主对话</RouterLink>
              <RouterLink v-for="side in sides" :key="side.id" :to="side.verificationId ? { name: 'career-skills', params: { id: side.verificationId } } : { name: 'workspace', query: { side: side.id } }"
                          :aria-current="selectedSide?.id === side.id ? 'page' : undefined">
                {{ side.verificationId ? side.title.slice(0, side.title.lastIndexOf(' · ')) : side.title }}
              </RouterLink>
            </nav>
            <form class="side-form" @submit.prevent="createSide">
              <label for="side-title">新建旁聊或通用学习主题</label>
              <input id="side-title" v-model="sideTitle" maxlength="80" placeholder="例如：复盘数据库项目">
              <button class="button button-quiet" :disabled="creatingSide || !sideTitle.trim()" type="submit">创建旁聊</button>
              <p v-if="sideError" class="notice notice-error" role="alert">{{ sideError }}</p>
            </form>
          </section>
        </aside>
      </div>
    </template>
  </main>
</template>

<style scoped>
.workspace-page { width: min(100% - 2rem, 71rem); }
.workspace-page h1 { margin-bottom: 1.15rem; }
.fit-banner { display: grid; grid-template-columns: auto minmax(12rem, 1fr) auto; gap: .75rem 1.25rem; align-items: center; padding: .9rem 1rem; margin-bottom: 1rem; }
.fit-banner h2 { font-size: 1.1rem; }
.fit-banner p { margin-top: .25rem; color: var(--muted); line-height: 1.5; }
.workspace-guidance { padding: .85rem 1rem; margin-bottom: 1rem; line-height: 1.55; overflow-wrap: anywhere; }
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
.side-chats { display: grid; gap: .65rem; border-top: 1px solid var(--outline); padding-top: .9rem; }
.side-chats nav { display: grid; gap: .25rem; max-height: 13rem; overflow: auto; }
.side-chats nav a { display: block; padding: .55rem .65rem; border-radius: .4rem; text-decoration: none; overflow-wrap: anywhere; }
.side-chats nav a[aria-current="page"] { font-weight: 700; background: #e6eee3; }
.side-chats nav a:hover { background: #edf1e8; }
.side-form { display: grid; gap: .45rem; }
.side-form label { font-size: .85rem; font-weight: 650; }
.side-form input { min-width: 0; width: 100%; padding: .55rem; border: 1px solid var(--outline); border-radius: .45rem; }
.workspace-feedback { padding: 1.1rem; }
.workspace-feedback .button { margin-top: .7rem; }

@media (max-width: 680px) {
  .workspace-layout, .fit-banner { grid-template-columns: minmax(0, 1fr); }
  .conversation { min-height: 14rem; }
}
</style>
